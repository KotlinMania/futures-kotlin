// port-lint: source futures-util/src/stream/stream/concat.rs
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
    private var done: Boolean = false

    public companion object {
        internal fun <T> new(stream: Stream<List<T>>): Concat<T> = Concat(stream)
    }

    override fun isTerminated(): Boolean = done

    override fun poll(context: TaskContext): Poll<List<T>> {
        while (true) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> accum.addAll(y.value)
                        Yield.End -> {
                            done = true
                            return Poll.Ready(accum.toList())
                        }
                    }
                }
                Poll.Pending -> return Poll.Pending
            }
        }
    }
}

/**
 * Concatenates all items of a stream into a single collection.
 */
@HiddenFromObjC
public fun <T> Stream<List<T>>.concat(): Concat<T> = Concat.new(this)
