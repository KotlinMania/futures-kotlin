// port-lint: source stream/stream/peek.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * A stream that implements a peek method.
 */
@HiddenFromObjC
public class Peekable<T>(
    stream: Stream<T>,
) : FusedStream<T> {
    private val stream: StreamFuse<T> = stream.fuse()
    private var peeked: T? = null
    private var hasPeeked: Boolean = false

    public companion object {
        internal fun <T> new(stream: Stream<T>): Peekable<T> = Peekable(stream)
    }

    override fun isTerminated(): Boolean =
        !hasPeeked && stream.isTerminated()

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
        object : FusedFuture<T?> {
            private var completed = false

            override fun isTerminated(): Boolean = completed

            override fun poll(context: TaskContext): Poll<T?> {
                check(!completed) { "NextIf polled after completion" }
                return when (val p = this@Peekable.pollNext(context)) {
                    Poll.Pending -> {
                        Poll.Pending
                    }

                    is Poll.Ready -> {
                        completed = true
                        when (val item = p.value) {
                            is Yield.Value -> {
                                if (predicate(item.value)) {
                                    Poll.Ready(item.value)
                                } else {
                                    check(!hasPeeked)
                                    peeked = item.value
                                    hasPeeked = true
                                    Poll.Ready(null)
                                }
                            }

                            Yield.End -> {
                                check(!hasPeeked)
                                Poll.Ready(null)
                            }
                        }
                    }
                }
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
        val upper = hint.upper?.let { if (it > Int.MAX_VALUE - peekLen) null else it + peekLen }
        return SizeHint(lower, upper)
    }
}

/**
 * Turns a stream into a peekable stream.
 */
@HiddenFromObjC
public fun <T> Stream<T>.peekable(): Peekable<T> = Peekable.new(this)
