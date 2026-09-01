// port-lint: source stream/stream/enumerate.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [enumerate] method.
 */
@HiddenFromObjC
public class Enumerate<T>(
    private val stream: Stream<T>,
) : FusedStream<IndexedValue<T>> {
    private var count: Int = 0

    public companion object {
        internal fun <T> new(stream: Stream<T>): Enumerate<T> = Enumerate(stream)
    }

    /**
     * Acquires a reference to the underlying stream that this combinator is pulling from.
     */
    public fun getRef(): Stream<T> = stream

    /**
     * Consumes this combinator, returning the underlying stream.
     */
    public fun intoInner(): Stream<T> = stream

    override fun isTerminated(): Boolean =
        (stream as? FusedStream<*>)?.isTerminated() ?: false

    override fun pollNext(context: TaskContext): Poll<Yield<IndexedValue<T>>> =
        when (val p = stream.pollNext(context)) {
            is Poll.Ready -> {
                when (val y = p.value) {
                    is Yield.Value -> {
                        val prev = count
                        count += 1
                        Poll.Ready(Yield.Value(IndexedValue(prev, y.value)))
                    }
                    Yield.End -> Poll.Ready(Yield.End)
                }
            }
            Poll.Pending -> Poll.Pending
        }

    override fun sizeHint(): SizeHint = stream.sizeHint()
}

/**
 * Gives the current iteration count as well as the next value.
 */
@HiddenFromObjC
public fun <T> Stream<T>.enumerate(): Enumerate<T> = Enumerate.new(this)
