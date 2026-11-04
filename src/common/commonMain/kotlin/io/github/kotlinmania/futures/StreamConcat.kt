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
    private val accum = mutableListOf<T>()
    private var hasAccum = false

    public companion object {
        internal fun <T> new(stream: Stream<List<T>>): Concat<T> = Concat(stream)
    }

    override fun isTerminated(): Boolean =
        !hasAccum && ((stream as? FusedStream<*>)?.isTerminated() ?: false)

    override fun poll(context: TaskContext): Poll<List<T>> {
        while (true) {
            when (val result = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val item = result.value) {
                        is Yield.Value -> {
                            hasAccum = true
                            accum.addAll(item.value)
                        }

                        Yield.End -> {
                            val completed = accum.toList()
                            accum.clear()
                            hasAccum = false
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
