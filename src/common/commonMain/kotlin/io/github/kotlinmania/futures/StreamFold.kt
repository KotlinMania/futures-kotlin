// port-lint: source stream/stream/fold.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [fold] method.
 */
@HiddenFromObjC
public class Fold<T, R>(
    private val stream: Stream<T>,
    accum: R,
    private val operation: (R, T) -> Future<R>,
) : FusedFuture<R> {
    private var accum: R? = accum
    private var hasAccum = true
    private var pendingFuture: Future<R>? = null

    public companion object {
        internal fun <T, R> new(stream: Stream<T>, initial: R, operation: (R, T) -> Future<R>): Fold<T, R> =
            Fold(stream, initial, operation)
    }

    override fun isTerminated(): Boolean = !hasAccum && pendingFuture == null

    @Suppress("UNCHECKED_CAST")
    override fun poll(context: TaskContext): Poll<R> {
        while (true) {
            val future = pendingFuture
            if (future != null) {
                when (val polled = future.poll(context)) {
                    is Poll.Ready -> {
                        accum = polled.value
                        hasAccum = true
                        pendingFuture = null
                    }

                    Poll.Pending -> {
                        return Poll.Pending
                    }
                }
            } else if (hasAccum) {
                when (val polled = stream.pollNext(context)) {
                    is Poll.Ready -> {
                        val current = accum as R
                        accum = null
                        hasAccum = false
                        when (val item = polled.value) {
                            is Yield.Value -> {
                                pendingFuture = operation(current, item.value)
                            }

                            Yield.End -> {
                                return Poll.Ready(current)
                            }
                        }
                    }

                    Poll.Pending -> {
                        return Poll.Pending
                    }
                }
            } else {
                throw RuntimeException("Fold polled after completion")
            }
        }
    }
}

/**
 * Executes asynchronous accumulating operations in order, evaluating to the final accumulator.
 */
@HiddenFromObjC
public fun <T, R> Stream<T>.fold(initial: R, operation: (R, T) -> Future<R>): Fold<T, R> =
    Fold.new(this, initial, operation)
