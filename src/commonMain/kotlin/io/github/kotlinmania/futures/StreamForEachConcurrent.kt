// port-lint: source stream/stream/for_each_concurrent.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [forEachConcurrent] method.
 */
@HiddenFromObjC
public class ForEachConcurrent<T>(
    private val stream: Stream<T>,
    private val limit: Int?,
    private val action: (T) -> Future<Unit>,
) : FusedFuture<Unit> {
    private val futures = mutableListOf<Future<Unit>>()
    private var streamDone: Boolean = false

    init {
        if (limit != null) {
            require(limit > 0) { "limit must be greater than 0" }
        }
    }

    public companion object {
        internal fun <T> new(
            stream: Stream<T>,
            limit: Int?,
            action: (T) -> Future<Unit>,
        ): ForEachConcurrent<T> = ForEachConcurrent(stream, limit, action)
    }

    override fun isTerminated(): Boolean = streamDone && futures.isEmpty()

    override fun poll(context: TaskContext): Poll<Unit> {
        while (true) {
            var madeProgress = false

            while (!streamDone && (limit == null || futures.size < limit)) {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> {
                                futures.add(action(y.value))
                                madeProgress = true
                            }
                            Yield.End -> {
                                streamDone = true
                                madeProgress = true
                            }
                        }
                    }
                    Poll.Pending -> break
                }
            }

            var i = 0
            while (i < futures.size) {
                when (futures[i].poll(context)) {
                    is Poll.Ready -> {
                        futures.removeAt(i)
                        madeProgress = true
                    }
                    Poll.Pending -> i++
                }
            }

            if (streamDone && futures.isEmpty()) {
                return Poll.Ready(Unit)
            }

            if (!madeProgress) {
                return Poll.Pending
            }
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
