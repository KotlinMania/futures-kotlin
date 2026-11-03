// port-lint: source stream/stream/skip_while.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [skipWhile] method.
 */
@HiddenFromObjC
public class SkipWhile<T>(
    private val stream: Stream<T>,
    private val predicate: (T) -> Future<Boolean>,
) : FusedStream<T> {
    private var pendingFuture: Future<Boolean>? = null
    private var pendingItem: Yield.Value<T>? = null
    private var doneSkipping: Boolean = false

    public companion object {
        internal fun <T> new(stream: Stream<T>, predicate: (T) -> Future<Boolean>): SkipWhile<T> =
            SkipWhile(stream, predicate)
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
        pendingItem == null && ((stream as? FusedStream<*>)?.isTerminated() ?: false)

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        if (doneSkipping) {
            return stream.pollNext(context)
        }
        while (true) {
            val future = pendingFuture
            if (future != null) {
                when (val result = future.poll(context)) {
                    is Poll.Ready -> {
                        val item = pendingItem
                        pendingItem = null
                        pendingFuture = null
                        if (!result.value) {
                            doneSkipping = true
                            return Poll.Ready(item ?: Yield.End)
                        }
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
        if (doneSkipping) return stream.sizeHint()
        val (_, upper) = stream.sizeHint()
        val pendingLength = if (pendingItem == null) 0 else 1
        val adjustedUpper = upper?.let { if (it > Int.MAX_VALUE - pendingLength) null else it + pendingLength }
        return SizeHint(0, adjustedUpper)
    }
}

/**
 * Skips elements in the stream until the predicate returns false.
 */
@HiddenFromObjC
public fun <T> Stream<T>.skipWhile(predicate: (T) -> Future<Boolean>): SkipWhile<T> =
    SkipWhile.new(this, predicate)
