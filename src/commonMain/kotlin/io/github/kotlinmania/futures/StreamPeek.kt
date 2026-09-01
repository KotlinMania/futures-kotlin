// port-lint: source stream/stream/peek.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * A stream that implements a peek method.
 */
@HiddenFromObjC
public class Peekable<T>(
    private val stream: Stream<T>,
) : FusedStream<T> {
    private var peeked: T? = null
    private var hasPeeked: Boolean = false

    public companion object {
        internal fun <T> new(stream: Stream<T>): Peekable<T> = Peekable(stream)
    }

    override fun isTerminated(): Boolean =
        !hasPeeked && ((stream as? FusedStream<*>)?.isTerminated() ?: false)

    public fun pollPeek(context: TaskContext): Poll<T?> {
        if (hasPeeked) {
            return Poll.Ready(peeked)
        }
        return when (val p = stream.pollNext(context)) {
            is Poll.Ready -> {
                when (val y = p.value) {
                    is Yield.Value -> {
                        peeked = y.value
                        hasPeeked = true
                        Poll.Ready(y.value)
                    }
                    Yield.End -> {
                        peeked = null
                        hasPeeked = false
                        Poll.Ready(null)
                    }
                }
            }
            Poll.Pending -> Poll.Pending
        }
    }

    public fun peek(): Future<T?> =
        object : Future<T?> {
            override fun poll(context: TaskContext): Poll<T?> = pollPeek(context)
        }

    public fun nextIf(predicate: (T) -> Boolean): Future<T?> =
        object : Future<T?> {
            override fun poll(context: TaskContext): Poll<T?> =
                when (val p = pollPeek(context)) {
                    is Poll.Ready -> {
                        val item = p.value
                        if (item != null && predicate(item)) {
                            hasPeeked = false
                            peeked = null
                            Poll.Ready(item)
                        } else {
                            Poll.Ready(null)
                        }
                    }
                    Poll.Pending -> Poll.Pending
                }
        }

    public fun nextIfEq(expected: T): Future<T?> = nextIf { it == expected }

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        if (hasPeeked) {
            hasPeeked = false
            val item = peeked
            peeked = null
            @Suppress("UNCHECKED_CAST")
            return Poll.Ready(Yield.Value(item as T))
        }
        return stream.pollNext(context)
    }

    override fun sizeHint(): SizeHint {
        val peekLen = if (hasPeeked) 1 else 0
        val hint = stream.sizeHint()
        val lower = (hint.lower.toLong() + peekLen).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val upper = hint.upper?.let { (it.toLong() + peekLen).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        return SizeHint(lower, upper)
    }
}

/**
 * Turns a stream into a peekable stream.
 */
@HiddenFromObjC
public fun <T> Stream<T>.peekable(): Peekable<T> = Peekable.new(this)
