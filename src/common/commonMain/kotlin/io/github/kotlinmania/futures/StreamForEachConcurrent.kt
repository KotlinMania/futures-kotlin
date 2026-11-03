// port-lint: source stream/stream/for_each_concurrent.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [forEachConcurrent] method.
 */
@HiddenFromObjC
public class ForEachConcurrent<T>(
    stream: Stream<T>,
    limit: Int?,
    private val action: (T) -> Future<Unit>,
) : FusedFuture<Unit> {
    private var stream: Stream<T>? = stream
    private val futures = FuturesUnordered<Unit>()

    // Note: `limit` = 0 gets ignored.
    private val limit: Int? = limit?.takeIf { it != 0 }

    init {
        require(limit == null || limit >= 0) { "limit must not be negative" }
    }

    public companion object {
        internal fun <T> new(
            stream: Stream<T>,
            limit: Int?,
            action: (T) -> Future<Unit>,
        ): ForEachConcurrent<T> = ForEachConcurrent(stream, limit, action)
    }

    override fun isTerminated(): Boolean = stream == null && futures.isEmpty()

    override fun poll(context: TaskContext): Poll<Unit> {
        while (true) {
            var madeProgressThisIter = false

            // Check if we've already created a number of futures greater than `limit`.
            if (limit == null || limit > futures.len()) {
                val input = stream
                if (input != null) {
                    when (val result = input.pollNext(context)) {
                        is Poll.Ready -> {
                            when (val item = result.value) {
                                is Yield.Value -> {
                                    madeProgressThisIter = true
                                    futures.push(action(item.value))
                                }

                                Yield.End -> {
                                    stream = null
                                }
                            }
                        }

                        Poll.Pending -> {}
                    }
                }
            }

            when (val result = futures.pollNext(context)) {
                is Poll.Ready -> {
                    when (result.value) {
                        is Yield.Value -> {
                            madeProgressThisIter = true
                        }

                        Yield.End -> {
                            if (stream == null) return Poll.Ready(Unit)
                        }
                    }
                }

                Poll.Pending -> {}
            }

            if (!madeProgressThisIter) return Poll.Pending
        }
    }
}

/**
 * Runs the provided future-returning action on up to [limit] elements concurrently.
 */
@HiddenFromObjC
public fun <T> Stream<T>.forEachConcurrent(
    limit: Int?,
    action: (T) -> Future<Unit>,
): ForEachConcurrent<T> = ForEachConcurrent.new(this, limit, action)
