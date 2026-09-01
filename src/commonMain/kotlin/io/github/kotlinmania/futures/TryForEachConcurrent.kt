// port-lint: source stream/try_stream/try_for_each_concurrent.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [tryForEachConcurrent] method.
 */
@HiddenFromObjC
public class TryForEachConcurrent<T, E>(
    private val stream: Stream<Try<T, E>>,
    private val limit: Int?,
    private val action: (T) -> Future<Try<Unit, E>>,
) : FusedFuture<Try<Unit, E>> {
    private val inFlight = mutableListOf<Future<Try<Unit, E>>>()
    private var streamDone: Boolean = false
    private var isFutureTerminated: Boolean = false

    init {
        if (limit != null) {
            require(limit > 0) { "limit must be greater than 0 if specified" }
        }
    }

    public companion object {
        internal fun <T, E> new(
            stream: Stream<Try<T, E>>,
            limit: Int?,
            action: (T) -> Future<Try<Unit, E>>,
        ): TryForEachConcurrent<T, E> = TryForEachConcurrent(stream, limit, action)
    }

    override fun isTerminated(): Boolean = isFutureTerminated

    override fun poll(context: TaskContext): Poll<Try<Unit, E>> {
        if (isFutureTerminated) {
            return Poll.ready(Try.ok(Unit))
        }

        while (true) {
            var madeProgressThisIter = false

            if (limit == null || inFlight.size < limit) {
                if (!streamDone) {
                    when (val pollResult = stream.pollNext(context)) {
                        is Poll.Ready -> {
                            when (val y = pollResult.value) {
                                is Yield.Value -> {
                                    when (val item = y.value) {
                                        is Try.Ok -> {
                                            madeProgressThisIter = true
                                            inFlight.add(action(item.value))
                                        }
                                        is Try.Err -> {
                                            streamDone = true
                                            inFlight.clear()
                                            isFutureTerminated = true
                                            return Poll.ready(Try.err(item.error))
                                        }
                                    }
                                }
                                Yield.End -> {
                                    streamDone = true
                                }
                            }
                        }
                        Poll.Pending -> {}
                    }
                }
            }

            var i = 0
            while (i < inFlight.size) {
                when (val p = inFlight[i].poll(context)) {
                    is Poll.Ready -> {
                        inFlight.removeAt(i)
                        when (val res = p.value) {
                            is Try.Ok -> {
                                madeProgressThisIter = true
                            }
                            is Try.Err -> {
                                streamDone = true
                                inFlight.clear()
                                isFutureTerminated = true
                                return Poll.ready(Try.err(res.error))
                            }
                        }
                    }
                    Poll.Pending -> i++
                }
            }

            if (streamDone && inFlight.isEmpty()) {
                isFutureTerminated = true
                return Poll.ready(Try.ok(Unit))
            }

            if (!madeProgressThisIter) {
                return Poll.pending()
            }
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
