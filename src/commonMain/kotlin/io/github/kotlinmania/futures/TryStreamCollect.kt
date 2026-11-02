// port-lint: source stream/try_stream/try_collect.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [tryCollect] method.
 */
@HiddenFromObjC
public class TryCollect<T, E>(
    private val stream: TryStream<T, E>,
) : FusedFuture<Try<List<T>, E>> {
    private var items = mutableListOf<T>()

    public companion object {
        internal fun <T, E> new(stream: TryStream<T, E>): TryCollect<T, E> = TryCollect(stream)
    }

    override fun isTerminated(): Boolean =
        (stream as? FusedStream<*>)?.isTerminated() ?: false

    override fun poll(context: TaskContext): Poll<Try<List<T>, E>> {
        while (true) {
            when (val result = stream.tryPollNext(context)) {
                is Poll.Ready -> {
                    when (val item = result.value) {
                        is Yield.Value -> {
                            when (val outcome = item.value) {
                                is Try.Ok -> {
                                    items.add(outcome.value)
                                }

                                is Try.Err -> {
                                    return Poll.Ready(Try.Err(outcome.error))
                                }
                            }
                        }

                        Yield.End -> {
                            val completed = items
                            items = mutableListOf()
                            return Poll.Ready(Try.Ok(completed))
                        }
                    }
                }

                Poll.Pending -> {
                    return Poll.Pending
                }
            }
        }
    }
}
