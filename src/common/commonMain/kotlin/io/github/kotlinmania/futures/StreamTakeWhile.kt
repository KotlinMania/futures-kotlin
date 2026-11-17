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
    private val predicate: (T) -> Future<Boolean>,
) : FusedStream<T> {
    private var pendingFuture: Future<Boolean>? = null
    private var pendingItem: Yield.Value<T>? = null
    private var doneTaking: Boolean = false

    public companion object {
        internal fun <T> new(stream: Stream<T>, predicate: (T) -> Future<Boolean>): TakeWhile<T> =
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
        doneTaking || (pendingItem == null && ((stream as? FusedStream<*>)?.isTerminated() ?: false))

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        if (doneTaking) return Poll.Ready(Yield.End)
        while (true) {
            val future = pendingFuture
            if (future != null) {
                when (val result = future.poll(context)) {
                    is Poll.Ready -> {
                        val item = pendingItem
                        pendingItem = null
                        pendingFuture = null
                        if (result.value) return Poll.Ready(item ?: Yield.End)
                        doneTaking = true
                        return Poll.Ready(Yield.End)
                    }

                    Poll.Pending -> {
                        return Poll.Pending
                    }
                }
            } else {
                when (val result = stream.pollNext(context)) {
                    is Poll.Ready -> {
                        when (val item = result.value) {
                            is Yield.Value -> {
                                pendingFuture = predicate(item.value)
                                pendingItem = item
                            }

                            Yield.End -> {
                                return Poll.Ready(Yield.End)
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

    override fun sizeHint(): SizeHint {
        if (doneTaking) return SizeHint(0, 0)
        val (_, upper) = stream.sizeHint()
        val pendingLength = if (pendingItem == null) 0 else 1
        val adjustedUpper = upper?.let { if (it > Int.MAX_VALUE - pendingLength) null else it + pendingLength }
        return SizeHint(0, adjustedUpper)
    }
}

/**
 * Takes elements from this stream until the predicate returns false.
 */
@HiddenFromObjC
public fun <T> Stream<T>.takeWhile(predicate: (T) -> Future<Boolean>): TakeWhile<T> =
    TakeWhile.new(this, predicate)
