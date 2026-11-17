// port-lint: source stream/stream/for_each.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [forEach] method.
 */
@HiddenFromObjC
public class ForEach<T>(
    private val stream: Stream<T>,
    private val action: (T) -> Future<Unit>,
) : FusedFuture<Unit> {
    private var pendingFuture: Future<Unit>? = null

    public companion object {
        internal fun <T> new(stream: Stream<T>, action: (T) -> Future<Unit>): ForEach<T> = ForEach(stream, action)
    }

    override fun isTerminated(): Boolean =
        pendingFuture == null && ((stream as? FusedStream<*>)?.isTerminated() ?: false)

    override fun poll(context: TaskContext): Poll<Unit> {
        while (true) {
            val future = pendingFuture
            if (future != null) {
                when (future.poll(context)) {
                    is Poll.Ready -> {
                        pendingFuture = null
                    }

                    Poll.Pending -> {
                        return Poll.Pending
                    }
                }
            } else {
                when (val polled = stream.pollNext(context)) {
                    is Poll.Ready -> {
                        when (val item = polled.value) {
                            is Yield.Value -> {
                                pendingFuture = action(item.value)
                            }

                            Yield.End -> {
                                return Poll.Ready(Unit)
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
}

/**
 * Runs the provided asynchronous action on each element, waiting for its future before reading the next item.
 */
@HiddenFromObjC
public fun <T> Stream<T>.forEach(action: (T) -> Future<Unit>): ForEach<T> = ForEach.new(this, action)
