// port-lint: source futures-test/src/interleave_pending.rs
@file:OptIn(
    kotlin.experimental.ExperimentalObjCRefinement::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
)

package io.github.kotlinmania.futures

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.native.HiddenFromObjC

/**
 * Wrapper that interleaves [Poll.pending] in calls to poll a [Future].
 */
@HiddenFromObjC
public class InterleavePendingFuture<T>(
    private val future: Future<T>,
) : Future<T>,
    FusedFuture<T> {
    private val pended = AtomicBoolean(false)

    public fun getRef(): Future<T> = future

    public fun intoInner(): Future<T> = future

    override fun poll(context: TaskContext): Poll<T> =
        if (pended.load()) {
            val next = future.poll(context)
            if (next.isReady()) {
                pended.store(false)
            }
            next
        } else {
            context.waker.wakeByRef()
            pended.store(true)
            Poll.pending()
        }

    override fun isTerminated(): Boolean =
        (future as? FusedFuture<*>)?.isTerminated() ?: false
}

/**
 * Wrapper that interleaves [Poll.pending] in calls to poll a [Stream].
 */
@HiddenFromObjC
public class InterleavePendingStream<T>(
    private val stream: Stream<T>,
) : Stream<T>,
    FusedStream<T> {
    private val pended = AtomicBoolean(false)

    public fun getRef(): Stream<T> = stream

    public fun intoInner(): Stream<T> = stream

    override fun pollNext(context: TaskContext): Poll<Yield<T>> =
        if (pended.load()) {
            val next = stream.pollNext(context)
            if (next.isReady()) {
                pended.store(false)
            }
            next
        } else {
            context.waker.wakeByRef()
            pended.store(true)
            Poll.pending()
        }

    override fun sizeHint(): SizeHint = stream.sizeHint()

    override fun isTerminated(): Boolean =
        (stream as? FusedStream<*>)?.isTerminated() ?: false
}

/**
 * Wrapper that interleaves [Poll.pending] in calls to poll a [Sink].
 */
@HiddenFromObjC
public class InterleavePendingSink<Item, Error>(
    private val sink: Sink<Item, Error>,
) : Sink<Item, Error> {
    private val pended = AtomicBoolean(false)

    public fun getRef(): Sink<Item, Error> = sink

    public fun intoInner(): Sink<Item, Error> = sink

    override fun pollReady(context: TaskContext): Poll<SinkOutcome<Error>> =
        if (pended.load()) {
            val next = sink.pollReady(context)
            if (next.isReady()) {
                pended.store(false)
            }
            next
        } else {
            context.waker.wakeByRef()
            pended.store(true)
            Poll.pending()
        }

    override fun startSend(item: Item): SinkOutcome<Error> =
        sink.startSend(item)

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<Error>> =
        if (pended.load()) {
            val next = sink.pollFlush(context)
            if (next.isReady()) {
                pended.store(false)
            }
            next
        } else {
            context.waker.wakeByRef()
            pended.store(true)
            Poll.pending()
        }

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<Error>> =
        if (pended.load()) {
            val next = sink.pollClose(context)
            if (next.isReady()) {
                pended.store(false)
            }
            next
        } else {
            context.waker.wakeByRef()
            pended.store(true)
            Poll.pending()
        }
}

/**
 * Creates an [InterleavePendingFuture] wrapper for a [Future].
 */
@HiddenFromObjC
public fun <T> Future<T>.interleavePending(): InterleavePendingFuture<T> =
    InterleavePendingFuture(this)

/**
 * Creates an [InterleavePendingStream] wrapper for a [Stream].
 */
@HiddenFromObjC
public fun <T> Stream<T>.interleavePending(): InterleavePendingStream<T> =
    InterleavePendingStream(this)

/**
 * Creates an [InterleavePendingSink] wrapper for a [Sink].
 */
@HiddenFromObjC
public fun <Item, Error> Sink<Item, Error>.interleavePending(): InterleavePendingSink<Item, Error> =
    InterleavePendingSink(this)
