// port-lint: source stream/stream/count.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [count] method.
 */
@HiddenFromObjC
public class Count<T>(
    private val stream: Stream<T>,
) : FusedFuture<Int> {
    private var count: Int = 0
    private var done: Boolean = false

    public companion object {
        internal fun <T> new(stream: Stream<T>): Count<T> = Count(stream)
    }

    override fun isTerminated(): Boolean = done

    override fun poll(context: TaskContext): Poll<Int> {
        while (true) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (p.value) {
                        is Yield.Value -> count += 1
                        Yield.End -> {
                            done = true
                            return Poll.Ready(count)
                        }
                    }
                }
                Poll.Pending -> return Poll.Pending
            }
        }
    }
}

/**
 * Counts the number of items yielded by this stream.
 */
@HiddenFromObjC
public fun <T> Stream<T>.count(): Count<T> = Count.new(this)
