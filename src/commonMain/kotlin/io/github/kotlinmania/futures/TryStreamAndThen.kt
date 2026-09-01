// port-lint: source stream/try_stream/and_then.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [andThen] method.
 */
@HiddenFromObjC
public class TryStreamAndThen<T, E, R>(
    private val stream: TryStream<T, E>,
    private val transform: (T) -> Future<Try<R, E>>,
) : FusedStream<Try<R, E>> {
    private var pendingFuture: Future<Try<R, E>>? = null

    public companion object {
        internal fun <T, E, R> new(
            stream: TryStream<T, E>,
            transform: (T) -> Future<Try<R, E>>,
        ): TryStreamAndThen<T, E, R> = TryStreamAndThen(stream, transform)
    }

    /**
     * Acquires a reference to the underlying stream that this combinator is pulling from.
     */
    public fun getRef(): TryStream<T, E> = stream

    /**
     * Consumes this combinator, returning the underlying stream.
     */
    public fun intoInner(): TryStream<T, E> = stream

    override fun isTerminated(): Boolean =
        pendingFuture == null && ((stream as? FusedStream<*>)?.isTerminated() ?: false)

    override fun pollNext(context: TaskContext): Poll<Yield<Try<R, E>>> {
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
                when (val p = stream.tryPollNext(context)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> {
                                when (val res = y.value) {
                                    is Try.Ok -> pendingFuture = transform(res.value)
                                    is Try.Err -> return Poll.Ready(Yield.Value(Try.Err(res.error)))
                                }
                            }
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
