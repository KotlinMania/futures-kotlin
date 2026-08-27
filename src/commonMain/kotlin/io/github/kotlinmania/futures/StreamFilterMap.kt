// port-lint: source futures-util/src/stream/stream/filter_map.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [filterMap] method.
 */
@HiddenFromObjC
public class FilterMap<T, R>(
    private val stream: Stream<T>,
    private val transform: (T) -> R?,
) : FusedStream<R> {
    public companion object {
        internal fun <T, R> new(stream: Stream<T>, transform: (T) -> R?): FilterMap<T, R> = FilterMap(stream, transform)
    }

    override fun isTerminated(): Boolean =
        (stream as? FusedStream<*>)?.isTerminated() ?: false

    override fun pollNext(context: TaskContext): Poll<Yield<R>> {
        while (true) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            val mapped = transform(y.value)
                            if (mapped != null) {
                                return Poll.Ready(Yield.Value(mapped))
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
 * Filters and maps elements produced by this stream.
 */
@HiddenFromObjC
public fun <T, R> Stream<T>.filterMap(transform: (T) -> R?): FilterMap<T, R> = FilterMap.new(this, transform)
