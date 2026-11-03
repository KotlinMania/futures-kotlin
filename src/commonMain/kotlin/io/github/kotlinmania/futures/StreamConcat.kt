// port-lint: source stream/stream/concat.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [concat] method.
 */
@HiddenFromObjC
public class Concat<T>(
    private val stream: Stream<List<T>>,
) : FusedFuture<List<T>> {
    private var accum: MutableList<T>? = null

    public companion object {
        internal fun <T> new(stream: Stream<List<T>>): Concat<T> = Concat(stream)
    }

    override fun isTerminated(): Boolean =
        accum == null && ((stream as? FusedStream<*>)?.isTerminated() ?: false)

    override fun poll(context: TaskContext): Poll<List<T>> {
        while (true) {
            when (val result = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val item = result.value) {
                        is Yield.Value -> {
                            val current = accum
                            if (current != null) {
                                current.addAll(item.value)
                            } else {
                                accum = item.value.toMutableList()
                            }
                        }

                        Yield.End -> {
                            val completed = accum ?: mutableListOf()
                            accum = null
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
 * Concatenates all items of a stream into a single collection.
 */
@HiddenFromObjC
public fun <T> Stream<List<T>>.concat(): Concat<T> = Concat.new(this)
