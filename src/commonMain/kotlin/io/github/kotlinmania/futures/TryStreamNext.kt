// port-lint: source futures-util/src/stream/try_stream/try_next.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [tryNext] method.
 */
@HiddenFromObjC
public class TryNext<T, E>(
    private val stream: TryStream<T, E>,
) : FusedFuture<Try<T?, E>> {
    public companion object {
        internal fun <T, E> new(stream: TryStream<T, E>): TryNext<T, E> = TryNext(stream)
    }

    override fun isTerminated(): Boolean =
        (stream as? FusedStream<*>)?.isTerminated() ?: false

    override fun poll(context: TaskContext): Poll<Try<T?, E>> =
        when (val p = stream.tryPollNext(context)) {
            is Poll.Ready -> {
                when (val y = p.value) {
                    is Yield.Value -> {
                        when (val res = y.value) {
                            is Try.Ok -> Poll.Ready(Try.Ok(res.value))
                            is Try.Err -> Poll.Ready(Try.Err(res.error))
                        }
                    }
                    Yield.End -> Poll.Ready(Try.Ok(null))
                }
            }
            Poll.Pending -> Poll.Pending
        }
}
