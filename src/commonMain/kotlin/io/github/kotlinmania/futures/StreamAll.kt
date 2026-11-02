// port-lint: source stream/stream/all.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [all] method.
 */
@HiddenFromObjC
public class StreamAll<T>(
    private val stream: Stream<T>,
    private val predicate: (T) -> Future<Boolean>,
) : FusedFuture<Boolean> {
    private var done = false
    private var pendingFuture: Future<Boolean>? = null

    public companion object {
        internal fun <T> new(stream: Stream<T>, predicate: (T) -> Future<Boolean>): StreamAll<T> = StreamAll(stream, predicate)
    }

    override fun isTerminated(): Boolean = done && pendingFuture == null

    override fun poll(context: TaskContext): Poll<Boolean> {
        while (true) {
            val future = pendingFuture
            if (future != null) {
                when (val polled = future.poll(context)) {
                    is Poll.Ready -> {
                        pendingFuture = null
                        if (!polled.value) {
                            done = true
                            return Poll.Ready(false)
                        }
                    }

                    Poll.Pending -> {
                        return Poll.Pending
                    }
                }
            } else if (!done) {
                when (val polled = stream.pollNext(context)) {
                    is Poll.Ready -> {
                        when (val item = polled.value) {
                            is Yield.Value -> {
                                pendingFuture = predicate(item.value)
                            }

                            Yield.End -> {
                                done = true
                                return Poll.Ready(true)
                            }
                        }
                    }

                    Poll.Pending -> {
                        return Poll.Pending
                    }
                }
            } else {
                throw RuntimeException("All polled after completion")
            }
        }
    }
}

/**
 * Tests if every element of the stream matches an asynchronous predicate.
 */
@HiddenFromObjC
public fun <T> Stream<T>.all(predicate: (T) -> Future<Boolean>): StreamAll<T> = StreamAll.new(this, predicate)

/**
 * Tests if every element of the stream matches an asynchronous predicate.
 */
@HiddenFromObjC
public fun <T> Stream<T>.forAll(predicate: (T) -> Future<Boolean>): StreamAll<T> = StreamAll.new(this, predicate)
