// port-lint: source stream/stream/filter_map.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [filterMap] method.
 */
@HiddenFromObjC
public class FilterMap<T, R>(
    private val stream: Stream<T>,
    private val transform: (T) -> Future<R?>,
) : FusedStream<R> {
    private var pendingFuture: Future<R?>? = null

    public companion object {
        internal fun <T, R> new(stream: Stream<T>, transform: (T) -> Future<R?>): FilterMap<T, R> = FilterMap(stream, transform)
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

    override fun pollNext(context: TaskContext): Poll<Yield<R>> {
        while (true) {
            val future = pendingFuture
            if (future != null) {
                // We have an item in progress, poll that until it's done.
                when (val result = future.poll(context)) {
                    is Poll.Ready -> {
                        pendingFuture = null
                        val item = result.value
                        if (item != null) return Poll.Ready(Yield.Value(item))
                    }

                    Poll.Pending -> {
                        return Poll.Pending
                    }
                }
            } else {
                // No item in progress, but the stream is still going.
                when (val result = stream.pollNext(context)) {
                    is Poll.Ready -> {
                        when (val item = result.value) {
                            is Yield.Value -> {
                                pendingFuture = transform(item.value)
                            }

                            Yield.End -> {
                                // The stream is done.
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
        val pendingLength = if (pendingFuture == null) 0 else 1
        val adjustedUpper = upper?.let { if (it > Int.MAX_VALUE - pendingLength) null else it + pendingLength }
        return SizeHint(0, adjustedUpper)
    }
}

/**
 * Filters and maps elements produced by this stream using an asynchronous closure.
 */
@HiddenFromObjC
public fun <T, R> Stream<T>.filterMap(transform: (T) -> Future<R?>): FilterMap<T, R> = FilterMap.new(this, transform)
