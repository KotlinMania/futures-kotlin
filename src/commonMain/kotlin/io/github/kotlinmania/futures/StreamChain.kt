// port-lint: source futures-util/src/stream/stream/chain.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [chain] method.
 */
@HiddenFromObjC
public class Chain<T>(
    private var first: Stream<T>?,
    private val second: Stream<T>,
) : FusedStream<T> {
    public companion object {
        internal fun <T> new(first: Stream<T>, second: Stream<T>): Chain<T> = Chain(first, second)
    }

    override fun isTerminated(): Boolean {
        if (first != null) return false
        return (second as? FusedStream<*>)?.isTerminated() ?: false
    }

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        val f = first
        if (f != null) {
            when (val p = f.pollNext(context)) {
                is Poll.Ready -> {
                    when (p.value) {
                        is Yield.Value -> return p
                        Yield.End -> first = null
                    }
                }
                Poll.Pending -> return Poll.Pending
            }
        }
        return second.pollNext(context)
    }

    override fun sizeHint(): SizeHint {
        val hint1 = first?.sizeHint() ?: SizeHint(0, 0)
        val hint2 = second.sizeHint()
        val lower = (hint1.lower.toLong() + hint2.lower.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val upper =
            if (hint1.upper != null && hint2.upper != null) {
                (hint1.upper.toLong() + hint2.upper.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            } else {
                null
            }
        return SizeHint(lower, upper)
    }
}

/**
 * Chains another stream to the end of this stream.
 */
@HiddenFromObjC
public fun <T> Stream<T>.chain(other: Stream<T>): Chain<T> = Chain.new(this, other)
