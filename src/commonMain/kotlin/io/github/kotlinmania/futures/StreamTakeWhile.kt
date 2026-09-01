// port-lint: source stream/stream/take_while.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [takeWhile] method.
 */
@HiddenFromObjC
public class TakeWhile<T>(
    private val stream: Stream<T>,
    private val predicate: (T) -> Boolean,
) : FusedStream<T> {
    private var doneTaking: Boolean = false

    public companion object {
        internal fun <T> new(stream: Stream<T>, predicate: (T) -> Boolean): TakeWhile<T> =
            TakeWhile(stream, predicate)
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
        doneTaking || ((stream as? FusedStream<*>)?.isTerminated() ?: false)

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        if (doneTaking) return Poll.Ready(Yield.End)
        while (true) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            if (predicate(y.value)) {
                                return Poll.Ready(Yield.Value(y.value))
                            } else {
                                doneTaking = true
                                return Poll.Ready(Yield.End)
                            }
                        }
                        Yield.End -> {
                            doneTaking = true
                            return Poll.Ready(Yield.End)
                        }
                    }
                }
                Poll.Pending -> return Poll.Pending
            }
        }
    }

    override fun sizeHint(): SizeHint {
        if (doneTaking) return SizeHint(0, 0)
        val (_, upper) = stream.sizeHint()
        return SizeHint(0, upper)
    }
}

/**
 * Takes elements from this stream until the predicate returns false.
 */
@HiddenFromObjC
public fun <T> Stream<T>.takeWhile(predicate: (T) -> Boolean): TakeWhile<T> =
    TakeWhile.new(this, predicate)
