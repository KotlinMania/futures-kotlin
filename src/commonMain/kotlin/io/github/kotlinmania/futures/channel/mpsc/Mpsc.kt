// port-lint: source futures-channel/src/mpsc/mod.rs
@file:OptIn(
    kotlin.experimental.ExperimentalObjCRefinement::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
)

package io.github.kotlinmania.futures.channel.mpsc

import io.github.kotlinmania.futures.AtomicWaker
import io.github.kotlinmania.futures.FusedStream
import io.github.kotlinmania.futures.Lock
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.Sink
import io.github.kotlinmania.futures.SinkOutcome
import io.github.kotlinmania.futures.SizeHint
import io.github.kotlinmania.futures.Stream
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import io.github.kotlinmania.futures.TryLock
import io.github.kotlinmania.futures.Waker
import io.github.kotlinmania.futures.Yield
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.native.HiddenFromObjC

/**
 * Error type for [Sender] and [UnboundedSender] used as [Sink]s.
 */
@HiddenFromObjC
public class SendError internal constructor(
    private val isFull: Boolean,
) {
    /**
     * Returns true if this error is a result of the channel being full.
     */
    public fun isFull(): Boolean = isFull

    /**
     * Returns true if this error is a result of the receiver being dropped or closed.
     */
    public fun isDisconnected(): Boolean = !isFull

    override fun equals(other: Any?): Boolean =
        other is SendError && other.isFull == isFull

    override fun hashCode(): Int = isFull.hashCode()

    override fun toString(): String =
        if (isFull) "SendError(full)" else "SendError(disconnected)"
}

/**
 * Error type returned from [Sender.trySend] and [UnboundedSender.trySend].
 */
@HiddenFromObjC
public class TrySendError<T> internal constructor(
    private val sendError: SendError,
    private val value: T,
) {
    /**
     * Returns true if this error is a result of the channel being full.
     */
    public fun isFull(): Boolean = sendError.isFull()

    /**
     * Returns true if this error is a result of the receiver being dropped or closed.
     */
    public fun isDisconnected(): Boolean = sendError.isDisconnected()

    /**
     * Returns the message that was attempted to be sent but failed.
     */
    public fun intoInner(): T = value

    /**
     * Converts this error into a [SendError].
     */
    public fun intoSendError(): SendError = sendError

    override fun equals(other: Any?): Boolean =
        other is TrySendError<*> &&
            other.sendError == sendError &&
            other.value == value

    override fun hashCode(): Int =
        31 * sendError.hashCode() + (value?.hashCode() ?: 0)

    override fun toString(): String =
        "TrySendError($sendError, $value)"
}

/**
 * Error type returned from [Receiver.tryNext] and [UnboundedReceiver.tryNext].
 */
@HiddenFromObjC
public class TryRecvError internal constructor(
    private val isDisconnected: Boolean,
) {
    /**
     * Returns true if the channel has been closed or disconnected.
     */
    public fun isDisconnected(): Boolean = isDisconnected

    /**
     * Returns true if the channel is currently empty.
     */
    public fun isEmpty(): Boolean = !isDisconnected

    override fun equals(other: Any?): Boolean =
        other is TryRecvError && other.isDisconnected == isDisconnected

    override fun hashCode(): Int = isDisconnected.hashCode()

    override fun toString(): String =
        if (isDisconnected) "TryRecvError(disconnected)" else "TryRecvError(empty)"
}

internal class BoundedInner<T>(
    val buffer: Int,
) {
    val lock: Lock<Unit> = Lock(Unit)
    val queue: ArrayDeque<T> = ArrayDeque()
    val parkedSenders: ArrayDeque<Waker> = ArrayDeque()
    val recvWaker: AtomicWaker = AtomicWaker()
    val numSenders: AtomicInt = AtomicInt(1)
    val isClosed: AtomicBoolean = AtomicBoolean(false)
    val receiverDropped: AtomicBoolean = AtomicBoolean(false)

    fun effectiveCapacity(): Int = maxOf(buffer, 0)
}

/**
 * The transmission end of a bounded mpsc channel.
 */
@HiddenFromObjC
public class Sender<T> internal constructor(
    internal val inner: BoundedInner<T>,
) : Sink<T, SendError> {
    private var isDisconnected: Boolean = false

    override fun pollReady(context: TaskContext): Poll<SinkOutcome<SendError>> {
        if (isDisconnected || inner.receiverDropped.load() || inner.isClosed.load()) {
            return Poll.ready(SinkOutcome.err(SendError(isFull = false)))
        }

        val slot = inner.lock.tryLock()
        if (slot == null) {
            inner.recvWaker.register(context.waker)
            return Poll.pending()
        }
        try {
            val limit = inner.effectiveCapacity()
            if (inner.queue.size <= limit) {
                return Poll.ready(SinkOutcome.ready())
            }
            inner.parkedSenders.add(context.waker)
            return Poll.pending()
        } finally {
            slot.unlock()
        }
    }

    override fun startSend(item: T): SinkOutcome<SendError> {
        if (isDisconnected || inner.receiverDropped.load() || inner.isClosed.load()) {
            return SinkOutcome.err(SendError(isFull = false))
        }

        var acquired: TryLock<Unit>? = null
        while (acquired == null) {
            acquired = inner.lock.tryLock()
        }
        try {
            val limit = inner.effectiveCapacity()
            if (limit == 0 && inner.queue.isNotEmpty()) {
                return SinkOutcome.err(SendError(isFull = true))
            }
            if (limit > 0 && inner.queue.size > limit) {
                return SinkOutcome.err(SendError(isFull = true))
            }
            inner.queue.add(item)
        } finally {
            acquired.unlock()
        }
        inner.recvWaker.wake()
        return SinkOutcome.ready()
    }

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<SendError>> {
        if (isDisconnected || inner.receiverDropped.load()) {
            return Poll.ready(SinkOutcome.err(SendError(isFull = false)))
        }
        return Poll.ready(SinkOutcome.ready())
    }

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<SendError>> {
        disconnect()
        return Poll.ready(SinkOutcome.ready())
    }

    /**
     * Attempts to send a message without blocking.
     */
    public fun trySend(msg: T): Try<Unit, TrySendError<T>> {
        if (isDisconnected || inner.receiverDropped.load() || inner.isClosed.load()) {
            return Try.err(TrySendError(SendError(isFull = false), msg))
        }

        val slot =
            inner.lock.tryLock()
                ?: return Try.err(TrySendError(SendError(isFull = true), msg))
        try {
            val limit = inner.effectiveCapacity()
            if (limit == 0 && inner.queue.isNotEmpty()) {
                return Try.err(TrySendError(SendError(isFull = true), msg))
            }
            if (limit > 0 && inner.queue.size >= limit) {
                return Try.err(TrySendError(SendError(isFull = true), msg))
            }
            inner.queue.add(msg)
        } finally {
            slot.unlock()
        }
        inner.recvWaker.wake()
        return Try.ok(Unit)
    }

    /**
     * Returns whether the sender is closed or receiver has disconnected.
     */
    public fun isClosed(): Boolean =
        isDisconnected || inner.isClosed.load() || inner.receiverDropped.load()

    /**
     * Closes the channel so that no further messages can be sent.
     */
    public fun closeChannel() {
        inner.isClosed.store(true)
        inner.recvWaker.wake()
        var slot: TryLock<Unit>? = null
        while (slot == null) {
            slot = inner.lock.tryLock()
        }
        val senders = ArrayList(inner.parkedSenders)
        inner.parkedSenders.clear()
        slot.unlock()
        for (waker in senders) {
            waker.wakeByRef()
        }
    }

    /**
     * Returns true if both senders point to the same channel.
     */
    public fun sameReceiver(other: Sender<T>): Boolean =
        inner === other.inner

    /**
     * Disconnects this sender handle.
     */
    public fun disconnect() {
        if (!isDisconnected) {
            isDisconnected = true
            if (inner.numSenders.fetchAndAdd(-1) == 1) {
                inner.recvWaker.wake()
            }
        }
    }

    /**
     * Clones this sender handle to share the channel.
     */
    public fun clone(): Sender<T> {
        inner.numSenders.fetchAndAdd(1)
        return Sender(inner)
    }
}

/**
 * The receiving end of a bounded mpsc channel.
 */
@HiddenFromObjC
public class Receiver<T> internal constructor(
    internal val inner: BoundedInner<T>,
) : Stream<T>,
    FusedStream<T> {
    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        var slot: TryLock<Unit>? = null
        while (slot == null) {
            slot = inner.lock.tryLock()
        }
        var nextSender: Waker? = null
        val item: T?
        try {
            if (inner.queue.isNotEmpty()) {
                item = inner.queue.removeFirst()
                if (inner.parkedSenders.isNotEmpty()) {
                    nextSender = inner.parkedSenders.removeFirst()
                }
                slot.unlock()
                nextSender?.wakeByRef()
                return Poll.ready(Yield.value(item))
            }
            if (inner.isClosed.load() || inner.numSenders.load() <= 0) {
                slot.unlock()
                return Poll.ready(Yield.end())
            }
        } finally {
            try {
                slot.unlock()
            } catch (_: Throwable) {
                // Ignored
            }
        }

        inner.recvWaker.register(context.waker)

        var retrySlot: TryLock<Unit>? = null
        while (retrySlot == null) {
            retrySlot = inner.lock.tryLock()
        }
        try {
            if (inner.queue.isNotEmpty()) {
                val recheckItem = inner.queue.removeFirst()
                val wake = if (inner.parkedSenders.isNotEmpty()) inner.parkedSenders.removeFirst() else null
                retrySlot.unlock()
                wake?.wakeByRef()
                return Poll.ready(Yield.value(recheckItem))
            }
            if (inner.isClosed.load() || inner.numSenders.load() <= 0) {
                retrySlot.unlock()
                return Poll.ready(Yield.end())
            }
        } finally {
            try {
                retrySlot.unlock()
            } catch (_: Throwable) {
                // Ignored
            }
        }
        return Poll.pending()
    }

    /**
     * Attempts to read a message from the receiver without blocking.
     */
    public fun tryNext(): Try<T?, TryRecvError> {
        var slot: TryLock<Unit>? = null
        while (slot == null) {
            slot = inner.lock.tryLock()
        }
        try {
            if (inner.queue.isNotEmpty()) {
                val item = inner.queue.removeFirst()
                val wake = if (inner.parkedSenders.isNotEmpty()) inner.parkedSenders.removeFirst() else null
                slot.unlock()
                wake?.wakeByRef()
                return Try.ok(item)
            }
            if (inner.isClosed.load() || inner.numSenders.load() <= 0) {
                slot.unlock()
                return Try.ok(null)
            }
            slot.unlock()
            return Try.err(TryRecvError(isDisconnected = false))
        } catch (t: Throwable) {
            try {
                slot.unlock()
            } catch (_: Throwable) {
                // Ignored
            }
            throw t
        }
    }

    /**
     * Closes the receiver, preventing further sends and discarding unreceived items.
     */
    public fun close() {
        inner.receiverDropped.store(true)
        inner.isClosed.store(true)
        var slot: TryLock<Unit>? = null
        while (slot == null) {
            slot = inner.lock.tryLock()
        }
        val senders = ArrayList(inner.parkedSenders)
        inner.parkedSenders.clear()
        slot.unlock()
        for (waker in senders) {
            waker.wakeByRef()
        }
    }

    override fun isTerminated(): Boolean =
        (inner.isClosed.load() || inner.numSenders.load() <= 0) && inner.queue.isEmpty()

    override fun sizeHint(): SizeHint {
        var slot: TryLock<Unit>? = null
        while (slot == null) {
            slot = inner.lock.tryLock()
        }
        val count = inner.queue.size
        val closed = inner.isClosed.load() || inner.numSenders.load() <= 0
        slot.unlock()
        return SizeHint(lower = count, upper = if (closed) count else null)
    }
}

internal class UnboundedInner<T> {
    val lock: Lock<Unit> = Lock(Unit)
    val queue: ArrayDeque<T> = ArrayDeque()
    val recvWaker: AtomicWaker = AtomicWaker()
    val numSenders: AtomicInt = AtomicInt(1)
    val isClosed: AtomicBoolean = AtomicBoolean(false)
    val receiverDropped: AtomicBoolean = AtomicBoolean(false)
}

/**
 * The transmission end of an unbounded mpsc channel.
 */
@HiddenFromObjC
public class UnboundedSender<T> internal constructor(
    internal val inner: UnboundedInner<T>,
) : Sink<T, SendError> {
    private var isDisconnected: Boolean = false

    override fun pollReady(context: TaskContext): Poll<SinkOutcome<SendError>> {
        if (isDisconnected || inner.receiverDropped.load() || inner.isClosed.load()) {
            return Poll.ready(SinkOutcome.err(SendError(isFull = false)))
        }
        return Poll.ready(SinkOutcome.ready())
    }

    override fun startSend(item: T): SinkOutcome<SendError> {
        if (isDisconnected || inner.receiverDropped.load() || inner.isClosed.load()) {
            return SinkOutcome.err(SendError(isFull = false))
        }
        var slot: TryLock<Unit>? = null
        while (slot == null) {
            slot = inner.lock.tryLock()
        }
        inner.queue.add(item)
        slot.unlock()
        inner.recvWaker.wake()
        return SinkOutcome.ready()
    }

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<SendError>> {
        if (isDisconnected || inner.receiverDropped.load()) {
            return Poll.ready(SinkOutcome.err(SendError(isFull = false)))
        }
        return Poll.ready(SinkOutcome.ready())
    }

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<SendError>> {
        disconnect()
        return Poll.ready(SinkOutcome.ready())
    }

    /**
     * Sends a message along the unbounded channel without blocking.
     */
    public fun unboundedSend(msg: T): Try<Unit, TrySendError<T>> = trySend(msg)

    /**
     * Attempts to send a message along the unbounded channel.
     */
    public fun trySend(msg: T): Try<Unit, TrySendError<T>> {
        if (isDisconnected || inner.receiverDropped.load() || inner.isClosed.load()) {
            return Try.err(TrySendError(SendError(isFull = false), msg))
        }
        var slot: TryLock<Unit>? = null
        while (slot == null) {
            slot = inner.lock.tryLock()
        }
        inner.queue.add(msg)
        slot.unlock()
        inner.recvWaker.wake()
        return Try.ok(Unit)
    }

    /**
     * Returns whether the channel is closed or receiver disconnected.
     */
    public fun isClosed(): Boolean =
        isDisconnected || inner.isClosed.load() || inner.receiverDropped.load()

    /**
     * Closes the channel.
     */
    public fun closeChannel() {
        inner.isClosed.store(true)
        inner.recvWaker.wake()
    }

    /**
     * Returns true if both senders point to the same channel.
     */
    public fun sameReceiver(other: UnboundedSender<T>): Boolean =
        inner === other.inner

    /**
     * Disconnects this sender handle.
     */
    public fun disconnect() {
        if (!isDisconnected) {
            isDisconnected = true
            if (inner.numSenders.fetchAndAdd(-1) == 1) {
                inner.recvWaker.wake()
            }
        }
    }

    /**
     * Clones this sender handle.
     */
    public fun clone(): UnboundedSender<T> {
        inner.numSenders.fetchAndAdd(1)
        return UnboundedSender(inner)
    }
}

/**
 * The receiving end of an unbounded mpsc channel.
 */
@HiddenFromObjC
public class UnboundedReceiver<T> internal constructor(
    internal val inner: UnboundedInner<T>,
) : Stream<T>,
    FusedStream<T> {
    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        var slot: TryLock<Unit>? = null
        while (slot == null) {
            slot = inner.lock.tryLock()
        }
        try {
            if (inner.queue.isNotEmpty()) {
                val item = inner.queue.removeFirst()
                slot.unlock()
                return Poll.ready(Yield.value(item))
            }
            if (inner.isClosed.load() || inner.numSenders.load() <= 0) {
                slot.unlock()
                return Poll.ready(Yield.end())
            }
        } finally {
            try {
                slot.unlock()
            } catch (_: Throwable) {
                // Ignored
            }
        }

        inner.recvWaker.register(context.waker)

        var retrySlot: TryLock<Unit>? = null
        while (retrySlot == null) {
            retrySlot = inner.lock.tryLock()
        }
        try {
            if (inner.queue.isNotEmpty()) {
                val item = inner.queue.removeFirst()
                retrySlot.unlock()
                return Poll.ready(Yield.value(item))
            }
            if (inner.isClosed.load() || inner.numSenders.load() <= 0) {
                retrySlot.unlock()
                return Poll.ready(Yield.end())
            }
        } finally {
            try {
                retrySlot.unlock()
            } catch (_: Throwable) {
                // Ignored
            }
        }
        return Poll.pending()
    }

    /**
     * Attempts to read a message without blocking.
     */
    public fun tryNext(): Try<T?, TryRecvError> {
        var slot: TryLock<Unit>? = null
        while (slot == null) {
            slot = inner.lock.tryLock()
        }
        try {
            if (inner.queue.isNotEmpty()) {
                val item = inner.queue.removeFirst()
                slot.unlock()
                return Try.ok(item)
            }
            if (inner.isClosed.load() || inner.numSenders.load() <= 0) {
                slot.unlock()
                return Try.ok(null)
            }
            slot.unlock()
            return Try.err(TryRecvError(isDisconnected = false))
        } catch (t: Throwable) {
            try {
                slot.unlock()
            } catch (_: Throwable) {
                // Ignored
            }
            throw t
        }
    }

    /**
     * Closes the receiver.
     */
    public fun close() {
        inner.receiverDropped.store(true)
        inner.isClosed.store(true)
    }

    override fun isTerminated(): Boolean =
        (inner.isClosed.load() || inner.numSenders.load() <= 0) && inner.queue.isEmpty()

    override fun sizeHint(): SizeHint {
        var slot: TryLock<Unit>? = null
        while (slot == null) {
            slot = inner.lock.tryLock()
        }
        val count = inner.queue.size
        val closed = inner.isClosed.load() || inner.numSenders.load() <= 0
        slot.unlock()
        return SizeHint(lower = count, upper = if (closed) count else null)
    }
}

/**
 * Creates a bounded multi-producer, single-consumer channel.
 */
@HiddenFromObjC
public fun <T> channel(buffer: Int = 0): Pair<Sender<T>, Receiver<T>> {
    val inner = BoundedInner<T>(buffer)
    return Pair(Sender(inner), Receiver(inner))
}

/**
 * Creates an unbounded multi-producer, single-consumer channel.
 */
@HiddenFromObjC
public fun <T> unbounded(): Pair<UnboundedSender<T>, UnboundedReceiver<T>> {
    val inner = UnboundedInner<T>()
    return Pair(UnboundedSender(inner), UnboundedReceiver(inner))
}
