// port-lint: source stream/stream/collect.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [collect] method.
 */
@HiddenFromObjC
public class Collect<T>(
    private val stream: Stream<T>,
) : FusedFuture<List<T>> {
    private val collection = mutableListOf<T>()

    public companion object {
        internal fun <T> new(stream: Stream<T>): Collect<T> = Collect(stream)
    }

    override fun isTerminated(): Boolean =
        (stream as? FusedStream<*>)?.isTerminated() ?: false

    override fun poll(context: TaskContext): Poll<List<T>> {
        while (true) {
            when (val result = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val item = result.value) {
                        is Yield.Value -> {
                            collection.add(item.value)
                        }

                        Yield.End -> {
                            val completed = collection.toList()
                            collection.clear()
                            return Poll.Ready(completed)
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

/**
 * Collects all the items of a stream into a list.
 */
@HiddenFromObjC
public fun <T> Stream<T>.collect(): Collect<T> = Collect.new(this)
