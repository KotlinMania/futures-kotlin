// port-lint: source futures-util/src/stream/try_stream/try_flatten.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [tryFlatten] method.
 */
@HiddenFromObjC
public class TryStreamFlatten<T, E>(
    private val stream: TryStream<TryStream<T, E>, E>,
) : FusedStream<Try<T, E>> {
    private var nextStream: TryStream<T, E>? = null

    public companion object {
        internal fun <T, E> new(stream: TryStream<TryStream<T, E>, E>): TryStreamFlatten<T, E> =
            TryStreamFlatten(stream)
    }

    /**
     * Acquires a reference to the underlying stream that this combinator is pulling from.
     */
    public fun getRef(): TryStream<TryStream<T, E>, E> = stream

    /**
     * Consumes this combinator, returning the underlying stream.
     */
    public fun intoInner(): TryStream<TryStream<T, E>, E> = stream

    override fun isTerminated(): Boolean =
        nextStream == null && ((stream as? FusedStream<*>)?.isTerminated() ?: false)

    override fun pollNext(context: TaskContext): Poll<Yield<Try<T, E>>> {
        while (true) {
            val next = nextStream
            if (next != null) {
                when (val p = next.tryPollNext(context)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> return Poll.Ready(Yield.Value(y.value))
                            Yield.End -> nextStream = null
                        }
                    }
                    Poll.Pending -> return Poll.Pending
                }
            } else {
                when (val p = stream.tryPollNext(context)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> {
                                when (val res = y.value) {
                                    is Try.Ok -> nextStream = res.value
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
}
