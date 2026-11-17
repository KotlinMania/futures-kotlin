// port-lint: source stream/try_stream/try_flatten_unordered.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [tryFlattenUnordered] method.
 */
@HiddenFromObjC
public class TryFlattenUnordered<T, E>(
    private val stream: Stream<Try<TryStream<T, E>, E>>,
    private val limit: Int? = null,
) : FusedStream<Try<T, E>> {
    private val innerStreams = mutableListOf<TryStream<T, E>>()
    private var baseStreamDone: Boolean = false

    init {
        if (limit != null) {
            require(limit > 0) { "limit must be greater than 0 if specified" }
        }
    }

    public companion object {
        internal fun <T, E> new(
            stream: Stream<Try<TryStream<T, E>, E>>,
            limit: Int? = null,
        ): TryFlattenUnordered<T, E> = TryFlattenUnordered(stream, limit)
    }

    override fun isTerminated(): Boolean = baseStreamDone && innerStreams.isEmpty()

    override fun pollNext(context: TaskContext): Poll<Yield<Try<T, E>>> {
        while (true) {
            var madeProgress = false

            if (limit == null || innerStreams.size < limit) {
                if (!baseStreamDone) {
                    when (val p = stream.pollNext(context)) {
                        is Poll.Ready -> {
                            when (val y = p.value) {
                                is Yield.Value -> {
                                    when (val item = y.value) {
                                        is Try.Ok -> {
                                            innerStreams.add(item.value)
                                            madeProgress = true
                                        }
                                        is Try.Err -> {
                                            return Poll.Ready(Yield.Value(Try.Err(item.error)))
                                        }
                                    }
                                }
                                Yield.End -> {
                                    baseStreamDone = true
                                }
                            }
                        }
                        Poll.Pending -> {}
                    }
                }
            }

            var i = 0
            while (i < innerStreams.size) {
                when (val p = innerStreams[i].pollNext(context)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> {
                                return Poll.Ready(Yield.Value(y.value))
                            }
                            Yield.End -> {
                                innerStreams.removeAt(i)
                                madeProgress = true
                            }
                        }
                    }
                    Poll.Pending -> i++
                }
            }

            if (baseStreamDone && innerStreams.isEmpty()) {
                return Poll.Ready(Yield.End)
            }

            if (!madeProgress) {
                return Poll.Pending
            }
        }
    }

    override fun sizeHint(): SizeHint {
        val hint = stream.sizeHint()
        val lower = (hint.lower.toLong() + innerStreams.size).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val upper = hint.upper?.let { (it.toLong() + innerStreams.size).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        return SizeHint(lower, upper)
    }
}

/**
 * Flattens a stream of try-streams concurrently in completion order.
 */
@HiddenFromObjC
public fun <T, E> TryStream<TryStream<T, E>, E>.tryFlattenUnordered(limit: Int? = null): TryFlattenUnordered<T, E> =
    TryFlattenUnordered.new(this, limit)
