// port-lint: source futures-util/src/stream/stream/skip.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [skip] method.
 */
@HiddenFromObjC
public class Skip<T>(
    private val stream: Stream<T>,
    private var remaining: Long,
) : FusedStream<T> {
    public companion object {
        internal fun <T> new(stream: Stream<T>, n: Long): Skip<T> = Skip(stream, n)
    }

    /**
     * Acquires a reference to the underlying stream that this combinator is pulling from.
     */
    public fun getRef(): Stream<T> = stream

    /**
     * Consumes this combinator, returning the underlying stream.
     */
    public fun intoInner(): Stream<T> = stream

    override fun isTerminated(): Boolean =
        (stream as? FusedStream<*>)?.isTerminated() ?: false

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        while (remaining > 0) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (p.value) {
                        is Yield.Value -> remaining -= 1
                        Yield.End -> {
                            remaining = 0
                            return Poll.Ready(Yield.End)
                        }
                    }
                }
                Poll.Pending -> return Poll.Pending
            }
        }
        return stream.pollNext(context)
    }

    override fun sizeHint(): SizeHint {
        val hint = stream.sizeHint()
        val lower = (hint.lower.toLong() - remaining).coerceAtLeast(0L).toInt()
        val upper = hint.upper?.let { (it.toLong() - remaining).coerceAtLeast(0L).toInt() }
        return SizeHint(lower, upper)
    }
}

/**
 * Creates a new stream that skips the first [n] items of the underlying stream.
 */
@HiddenFromObjC
public fun <T> Stream<T>.skip(n: Long): Skip<T> = Skip.new(this, n)

/**
 * Creates a new stream that skips the first [n] items of the underlying stream.
 */
@HiddenFromObjC
public fun <T> Stream<T>.skip(n: Int): Skip<T> = Skip.new(this, n.toLong())
