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
    private val predicate: (T) -> Future<Boolean>,
) : FusedStream<T> {
    private var pendingFuture: Future<Boolean>? = null
    private var pendingItem: Yield.Value<T>? = null

    public companion object {
        internal fun <T> new(stream: Stream<T>, predicate: (T) -> Future<Boolean>): Filter<T> = Filter(stream, predicate)
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
        pendingFuture == null && ((stream as? FusedStream<*>)?.isTerminated() ?: false)

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        while (true) {
            val future = pendingFuture
            if (future != null) {
                when (val result = future.poll(context)) {
                    is Poll.Ready -> {
                        pendingFuture = null
                        val item = pendingItem
                        pendingItem = null
                        if (result.value) return Poll.Ready(item ?: Yield.End)
                    }

                    Poll.Pending -> {
                        return Poll.Pending
                    }
                }
            } else {
                when (val result = stream.pollNext(context)) {
                    is Poll.Ready -> {
                        when (val item = result.value) {
                            is Yield.Value -> {
                                pendingFuture = predicate(item.value)
                                pendingItem = item
                            }

                            Yield.End -> {
                                return Poll.Ready(Yield.End)
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

    override fun sizeHint(): SizeHint {
        val (_, upper) = stream.sizeHint()
        val pendingLength = if (pendingItem == null) 0 else 1
        val adjustedUpper = upper?.let { if (it > Int.MAX_VALUE - pendingLength) null else it + pendingLength }
        return SizeHint(0, adjustedUpper)
    }
}

/**
 * Filters the items produced by this stream according to the provided predicate.
 */
@HiddenFromObjC
public fun <T> Stream<T>.filter(predicate: (T) -> Future<Boolean>): Filter<T> = Filter.new(this, predicate)
