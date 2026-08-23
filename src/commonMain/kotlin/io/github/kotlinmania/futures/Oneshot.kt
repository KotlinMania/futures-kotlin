// port-lint: source futures-channel/src/oneshot.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.native.HiddenFromObjC

/**
 * Error returned from a [Receiver] when the corresponding [Sender] is dropped.
 */
@HiddenFromObjC
public data object Canceled

/**
 * Internal state of the [Receiver] / [Sender] pair for synchronization.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class OneshotInner<T> {
    val complete = AtomicBoolean(false)
    val data = Lock<T?>(null)
    val rxTask = Lock<Waker?>(null)
    val txTask = Lock<Waker?>(null)

    fun send(item: T): Try<Unit, T> {
        if (complete.load()) {
            return Try.err(item)
        }

        val slot = data.tryLock() ?: return Try.err(item)
        slot.set(item)
        slot.unlock()

        if (complete.load()) {
            val retrySlot = data.tryLock()
            if (retrySlot != null) {
                val stored = retrySlot.get()
                retrySlot.set(null)
                retrySlot.unlock()
                if (stored != null) {
                    return Try.err(stored)
                }
            }
        }
        return Try.ok(Unit)
    }

    fun pollCanceled(context: TaskContext): Poll<Unit> {
        if (complete.load()) {
            return Poll.ready(Unit)
        }

        val handle = context.waker
        val slot = txTask.tryLock() ?: return Poll.ready(Unit)
        slot.set(handle)
        slot.unlock()

        return if (complete.load()) {
            Poll.ready(Unit)
        } else {
            Poll.pending()
        }
    }

    fun isCanceled(): Boolean = complete.load()

    fun dropTx() {
        complete.store(true)
        val rxSlot = rxTask.tryLock()
        if (rxSlot != null) {
            val task = rxSlot.get()
            rxSlot.set(null)
            rxSlot.unlock()
            task?.wakeByRef()
        }
        val txSlot = txTask.tryLock()
        if (txSlot != null) {
            txSlot.set(null)
            txSlot.unlock()
        }
    }

    fun closeRx() {
        complete.store(true)
        val txSlot = txTask.tryLock()
        if (txSlot != null) {
            val task = txSlot.get()
            txSlot.set(null)
            txSlot.unlock()
            task?.wakeByRef()
        }
    }

    fun tryRecv(): Try<T?, Canceled> {
        if (complete.load()) {
            val slot = data.tryLock()
            if (slot != null) {
                val item = slot.get()
                slot.set(null)
                slot.unlock()
                if (item != null) {
                    return Try.ok(item)
                }
            }
            return Try.err(Canceled)
        }
        return Try.ok(null)
    }

    fun recv(context: TaskContext): Poll<Try<T, Canceled>> {
        val done =
            if (complete.load()) {
                true
            } else {
                val task = context.waker
                val slot = rxTask.tryLock()
                if (slot != null) {
                    slot.set(task)
                    slot.unlock()
                    false
                } else {
                    true
                }
            }

        return if (done || complete.load()) {
            val slot = data.tryLock()
            if (slot != null) {
                val item = slot.get()
                slot.set(null)
                slot.unlock()
                if (item != null) {
                    return Poll.ready(Try.ok(item))
                }
            }
            Poll.ready(Try.err(Canceled))
        } else {
            Poll.pending()
        }
    }

    fun dropRx() {
        complete.store(true)
        val rxSlot = rxTask.tryLock()
        if (rxSlot != null) {
            rxSlot.set(null)
            rxSlot.unlock()
        }
        val txSlot = txTask.tryLock()
        if (txSlot != null) {
            val task = txSlot.get()
            txSlot.set(null)
            txSlot.unlock()
            task?.wakeByRef()
        }
    }
}

/**
 * A means of transmitting a single value to another task.
 */
@HiddenFromObjC
public class Sender<T> internal constructor(
    internal val inner: OneshotInner<T>,
) {
    /**
     * Completes this oneshot with a successful result.
     */
    public fun send(item: T): Try<Unit, T> {
        val res = inner.send(item)
        if (res is Try.Ok) {
            inner.dropTx()
        }
        return res
    }

    /**
     * Polls this sender to detect whether its associated [Receiver] has been dropped.
     */
    public fun pollCanceled(context: TaskContext): Poll<Unit> = inner.pollCanceled(context)

    /**
     * Creates a future that resolves when this sender's corresponding [Receiver] has hung up.
     */
    public fun cancellation(): Cancellation<T> = Cancellation(this)

    /**
     * Tests to see whether this sender's corresponding [Receiver] has been dropped.
     */
    public fun isCanceled(): Boolean = inner.isCanceled()

    /**
     * Tests to see whether this sender is connected to the given [receiver].
     */
    public fun isConnectedTo(receiver: Receiver<T>): Boolean = inner === receiver.inner

    /**
     * Explicitly close/drop this sender.
     */
    public fun close() {
        inner.dropTx()
    }
}

/**
 * A future that resolves when the receiving end of a channel has hung up.
 */
@HiddenFromObjC
public class Cancellation<T>(
    private val sender: Sender<T>,
) : Future<Unit> {
    override fun poll(context: TaskContext): Poll<Unit> = sender.pollCanceled(context)
}

/**
 * A future for a value that will be provided by another asynchronous task.
 */
@HiddenFromObjC
public class Receiver<T> internal constructor(
    internal val inner: OneshotInner<T>,
) : FusedFuture<Try<T, Canceled>> {
    /**
     * Gracefully close this receiver, preventing subsequent send attempts.
     */
    public fun close() {
        inner.closeRx()
    }

    /**
     * Attempts to receive a message outside of the context of a task.
     */
    public fun tryRecv(): Try<T?, Canceled> = inner.tryRecv()

    override fun poll(context: TaskContext): Poll<Try<T, Canceled>> = inner.recv(context)

    @OptIn(ExperimentalAtomicApi::class)
    override fun isTerminated(): Boolean {
        if (inner.complete.load()) {
            val slot = inner.data.tryLock()
            if (slot != null) {
                val hasData = slot.get() != null
                slot.unlock()
                if (hasData) {
                    return false
                }
            }
            return true
        }
        return false
    }
}

/**
 * Creates a new one-shot channel for sending a single value across asynchronous tasks.
 */
@HiddenFromObjC
public fun <T> oneshot(): Pair<Sender<T>, Receiver<T>> {
    val inner = OneshotInner<T>()
    return Pair(Sender(inner), Receiver(inner))
}

/**
 * Creates a new one-shot channel for sending a single value across asynchronous tasks.
 */
@HiddenFromObjC
public fun <T> channel(): Pair<Sender<T>, Receiver<T>> = oneshot()
