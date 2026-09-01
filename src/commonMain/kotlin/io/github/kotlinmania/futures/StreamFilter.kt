// port-lint: source stream/stream/filter.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [filter] method.
 */
@HiddenFromObjC
public class Filter<T>(
    private val stream: Stream<T>,
    private val predicate: (T) -> Boolean,
) : FusedStream<T> {
    public companion object {
        internal fun <T> new(stream: Stream<T>, predicate: (T) -> Boolean): Filter<T> = Filter(stream, predicate)
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

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        while (true) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            if (predicate(y.value)) {
                                return Poll.Ready(Yield.Value(y.value))
                            }
                        }
                        Yield.End -> return Poll.Ready(Yield.End)
                    }
                }
                Poll.Pending -> return Poll.Pending
            }
        }
    }

    override fun sizeHint(): SizeHint {
        val (_, upper) = stream.sizeHint()
        return SizeHint(0, upper)
    }
}

/**
 * Filters the items produced by this stream according to the provided predicate.
 */
@HiddenFromObjC
public fun <T> Stream<T>.filter(predicate: (T) -> Boolean): Filter<T> = Filter.new(this, predicate)
