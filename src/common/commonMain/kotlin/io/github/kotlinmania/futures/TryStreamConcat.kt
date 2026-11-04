// port-lint: source stream/try_stream/try_concat.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [tryConcat] method.
 */
@HiddenFromObjC
public class TryConcat<T, E>(
    private val stream: TryStream<List<T>, E>,
) : Future<Try<List<T>, E>> {
    private val accum = mutableListOf<T>()

    public companion object {
        internal fun <T, E> new(stream: TryStream<List<T>, E>): TryConcat<T, E> = TryConcat(stream)
    }

    override fun poll(context: TaskContext): Poll<Try<List<T>, E>> {
        while (true) {
            when (val p = stream.tryPollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            when (val res = y.value) {
                                is Try.Ok -> accum.addAll(res.value)
                                is Try.Err -> {
                                    return Poll.Ready(Try.Err(res.error))
                                }
                            }
                        }
                        Yield.End -> {
                            val completed = accum.toList()
                            accum.clear()
                            return Poll.Ready(Try.Ok(completed))
                        }
                    }
                }
                Poll.Pending -> return Poll.Pending
            }
        }
    }
}
