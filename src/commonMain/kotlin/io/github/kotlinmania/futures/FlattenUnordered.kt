// port-lint: source stream/stream/flatten_unordered.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [flattenUnordered] method.
 */
@HiddenFromObjC
public class FlattenUnordered<T>(
    private val stream: Stream<Stream<T>>,
    private val limit: Int? = null,
) : FusedStream<T> {
    private val innerStreams = mutableListOf<Stream<T>>()
    private var baseStreamDone: Boolean = false

    init {
        if (limit != null) {
            require(limit > 0) { "limit must be greater than 0 if specified" }
        }
    }

    public companion object {
        internal fun <T> new(stream: Stream<Stream<T>>, limit: Int? = null): FlattenUnordered<T> =
            FlattenUnordered(stream, limit)
    }

    override fun isTerminated(): Boolean = baseStreamDone && innerStreams.isEmpty()

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        while (true) {
            var madeProgress = false

            if (limit == null || innerStreams.size < limit) {
                if (!baseStreamDone) {
                    when (val p = stream.pollNext(context)) {
                        is Poll.Ready -> {
                            when (val y = p.value) {
                                is Yield.Value -> {
                                    innerStreams.add(y.value)
                                    madeProgress = true
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
 * Flattens a stream of streams concurrently in completion order.
 */
@HiddenFromObjC
public fun <T> Stream<Stream<T>>.flattenUnordered(limit: Int? = null): FlattenUnordered<T> =
    FlattenUnordered.new(this, limit)
