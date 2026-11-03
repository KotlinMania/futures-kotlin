// port-lint: source stream/try_stream/try_for_each_concurrent.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [tryForEachConcurrent] method.
 */
@HiddenFromObjC
public class TryForEachConcurrent<T, E>(
    stream: Stream<Try<T, E>>,
    limit: Int?,
    private val action: (T) -> Future<Try<Unit, E>>,
) : FusedFuture<Try<Unit, E>> {
    private var stream: Stream<Try<T, E>>? = stream
    private var futures = FuturesUnordered<Try<Unit, E>>()

    // Note: `limit` = 0 gets ignored.
    private val limit: Int? = limit?.takeIf { it != 0 }

    init {
        require(limit == null || limit >= 0) { "limit must not be negative" }
    }

    public companion object {
        internal fun <T, E> new(
            stream: Stream<Try<T, E>>,
            limit: Int?,
            action: (T) -> Future<Try<Unit, E>>,
        ): TryForEachConcurrent<T, E> = TryForEachConcurrent(stream, limit, action)
    }

    override fun isTerminated(): Boolean = stream == null && futures.isEmpty()

    override fun poll(context: TaskContext): Poll<Try<Unit, E>> {
        while (true) {
            var madeProgressThisIter = false

            // Check if we've already created a number of futures greater than `limit`.
            if (limit == null || limit > futures.len()) {
                val input = stream
                val result = input?.pollNext(context) ?: Poll.Ready(Yield.End)
                when (result) {
                    is Poll.Ready -> {
                        when (val item = result.value) {
                            is Yield.Value -> {
                                when (val outcome = item.value) {
                                    is Try.Ok -> {
                                        madeProgressThisIter = true
                                        futures.push(action(outcome.value))
                                    }

                                    is Try.Err -> {
                                        // Empty the stream and futures so that we know the future has completed.
                                        stream = null
                                        futures = FuturesUnordered()
                                        return Poll.Ready(Try.Err(outcome.error))
                                    }
                                }
                            }

                            Yield.End -> {
                                stream = null
                            }
                        }
                    }

                    Poll.Pending -> {}
                }
            }

            when (val result = futures.pollNext(context)) {
                is Poll.Ready -> {
                    when (val item = result.value) {
                        is Yield.Value -> {
                            when (val outcome = item.value) {
                                is Try.Ok -> {
                                    madeProgressThisIter = true
                                }

                                is Try.Err -> {
                                    // Empty the stream and futures so that we know the future has completed.
                                    stream = null
                                    futures = FuturesUnordered()
                                    return Poll.Ready(Try.Err(outcome.error))
                                }
                            }
                        }

                        Yield.End -> {
                            if (stream == null) return Poll.Ready(Try.Ok(Unit))
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
 * Runs an asynchronous action for each item produced by this stream concurrently, with an optional maximum concurrency limit.
 */
@HiddenFromObjC
public fun <T, E> TryStream<T, E>.tryForEachConcurrent(
    limit: Int? = null,
    action: (T) -> Future<Try<Unit, E>>,
): TryForEachConcurrent<T, E> = TryForEachConcurrent.new(this, limit, action)
