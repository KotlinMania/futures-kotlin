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
    private var accum: R?,
    private val operation: (R, T) -> R,
) : FusedFuture<R> {
    private var done: Boolean = false

    public companion object {
        internal fun <T, R> new(stream: Stream<T>, initial: R, operation: (R, T) -> R): Fold<T, R> =
            Fold(stream, initial, operation)
    }

    override fun isTerminated(): Boolean = done

    @Suppress("UNCHECKED_CAST")
    override fun poll(context: TaskContext): Poll<R> {
        if (done) throw RuntimeException("Fold polled after completion")
        while (true) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            val cur = accum as R
                            accum = operation(cur, y.value)
                        }
                        Yield.End -> {
                            done = true
                            val result = accum as R
                            accum = null
                            return Poll.Ready(result)
                        }
                    }
                }
                Poll.Pending -> return Poll.Pending
            }
        }
    }
}

/**
 * Executes an accumulating operation over a stream, evaluating to the final value.
 */
@HiddenFromObjC
public fun <T, R> Stream<T>.fold(initial: R, operation: (R, T) -> R): Fold<T, R> =
    Fold.new(this, initial, operation)
