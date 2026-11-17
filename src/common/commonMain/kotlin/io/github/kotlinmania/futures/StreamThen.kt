// port-lint: source stream/stream/then.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [then] method.
 */
@HiddenFromObjC
public class Then<T, R>(
    private val stream: Stream<T>,
    private val futureFactory: (T) -> Future<R>,
) : FusedStream<R> {
    private var pendingFuture: Future<R>? = null

    public companion object {
        internal fun <T, R> new(stream: Stream<T>, futureFactory: (T) -> Future<R>): Then<T, R> =
            Then(stream, futureFactory)
    }

    /**
     * Acquires a reference to the underlying stream that this combinator is pulling from.
     */
    public fun getRef(): Stream<T> = stream

    /**
     * Consumes this combinator, returning the underlying stream.
     */
    public fun intoInner(): Stream<T> = stream

    override fun isTerminated(): Boolean =
        pendingFuture == null && ((stream as? FusedStream<*>)?.isTerminated() ?: false)

    override fun pollNext(context: TaskContext): Poll<Yield<R>> {
        while (true) {
            val fut = pendingFuture
            if (fut != null) {
                when (val p = fut.poll(context)) {
                    is Poll.Ready -> {
                        pendingFuture = null
                        return Poll.Ready(Yield.Value(p.value))
                    }
                    Poll.Pending -> return Poll.Pending
                }
            } else {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> pendingFuture = futureFactory(y.value)
                            Yield.End -> return Poll.Ready(Yield.End)
                        }
                    }
                    Poll.Pending -> return Poll.Pending
                }
            }
        }
    }

    override fun sizeHint(): SizeHint {
        val futureLen = if (pendingFuture != null) 1 else 0
        val hint = stream.sizeHint()
        val lower = (hint.lower.toLong() + futureLen).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val upper = hint.upper?.let { (it.toLong() + futureLen).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        return SizeHint(lower, upper)
    }
}

/**
 * Computes a future for each item produced by this stream and yields the output of the future.
 */
@HiddenFromObjC
public fun <T, R> Stream<T>.then(futureFactory: (T) -> Future<R>): Then<T, R> = Then.new(this, futureFactory)
