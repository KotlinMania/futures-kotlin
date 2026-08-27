// port-lint: source futures-util/src/stream/stream/take.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.math.min
import kotlin.native.HiddenFromObjC

/**
 * Stream for the [take] method.
 */
@HiddenFromObjC
public class Take<T>(
    private val stream: Stream<T>,
    private var remaining: Long,
) : FusedStream<T> {
    public companion object {
        internal fun <T> new(stream: Stream<T>, n: Long): Take<T> = Take(stream, n)
    }

    override fun isTerminated(): Boolean {
        if (remaining <= 0) return true
        return (stream as? FusedStream<*>)?.isTerminated() ?: false
    }

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        if (remaining <= 0) {
            return Poll.Ready(Yield.End)
        }
        return when (val p = stream.pollNext(context)) {
            is Poll.Ready -> {
                when (val y = p.value) {
                    is Yield.Value -> {
                        remaining -= 1
                        Poll.Ready(Yield.Value(y.value))
                    }
                    Yield.End -> {
                        remaining = 0
                        Poll.Ready(Yield.End)
                    }
                }
            }
            Poll.Pending -> Poll.Pending
        }
    }

    override fun sizeHint(): SizeHint {
        if (remaining <= 0) return SizeHint(0, 0)
        val hint = stream.sizeHint()
        val lower = min(hint.lower.toLong(), remaining).toInt()
        val upper = hint.upper?.let { min(it.toLong(), remaining).toInt() } ?: (if (remaining <= Int.MAX_VALUE) remaining.toInt() else null)
        return SizeHint(lower, upper)
    }
}

/**
 * Creates a new stream of at most [n] items of the underlying stream.
 */
@HiddenFromObjC
public fun <T> Stream<T>.take(n: Long): Take<T> = Take.new(this, n)

/**
 * Creates a new stream of at most [n] items of the underlying stream.
 */
@HiddenFromObjC
public fun <T> Stream<T>.take(n: Int): Take<T> = Take.new(this, n.toLong())
