// port-lint: source stream/try_stream/try_take_while.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [tryTakeWhile] method.
 */
@HiddenFromObjC
public class TryStreamTakeWhile<T, E>(
    private val stream: TryStream<T, E>,
    private val predicate: (T) -> Future<Try<Boolean, E>>,
) : FusedStream<Try<T, E>> {
    private var pendingFuture: Future<Try<Boolean, E>>? = null
    private var pendingItem: Try.Ok<T>? = null
    private var doneTaking: Boolean = false

    public companion object {
        internal fun <T, E> new(
            stream: TryStream<T, E>,
            predicate: (T) -> Future<Try<Boolean, E>>,
        ): TryStreamTakeWhile<T, E> = TryStreamTakeWhile(stream, predicate)
    }

    /**
     * Acquires a reference to the underlying stream that this combinator is pulling from.
     */
    public fun getRef(): TryStream<T, E> = stream

    /**
     * Consumes this combinator, returning the underlying stream.
     */
    public fun intoInner(): TryStream<T, E> = stream

    override fun isTerminated(): Boolean =
        doneTaking || (pendingItem == null && ((stream as? FusedStream<*>)?.isTerminated() ?: false))

    override fun pollNext(context: TaskContext): Poll<Yield<Try<T, E>>> {
        if (doneTaking) {
            return Poll.Ready(Yield.End)
        }

        while (true) {
            val future = pendingFuture
            if (future != null) {
                when (val result = future.poll(context)) {
                    is Poll.Ready -> {
                        pendingFuture = null
                        when (val outcome = result.value) {
                            is Try.Ok -> {
                                val item = pendingItem
                                pendingItem = null
                                if (outcome.value) {
                                    return Poll.Ready(if (item == null) Yield.End else Yield.Value(item))
                                } else {
                                    doneTaking = true
                                    return Poll.Ready(Yield.End)
                                }
                            }

                            is Try.Err -> {
                                return Poll.Ready(Yield.Value(Try.Err(outcome.error)))
                            }
                        }
                    }

                    Poll.Pending -> {
                        return Poll.Pending
                    }
                }
            } else {
                when (val result = stream.tryPollNext(context)) {
                    is Poll.Ready -> {
                        when (val item = result.value) {
                            is Yield.Value -> {
                                when (val outcome = item.value) {
                                    is Try.Ok -> {
                                        pendingFuture = predicate(outcome.value)
                                        pendingItem = outcome
                                    }

                                    is Try.Err -> {
                                        return Poll.Ready(Yield.Value(Try.Err(outcome.error)))
                                    }
                                }
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
        if (doneTaking) {
            return SizeHint(0, 0)
        }
        val pendingLen = if (pendingItem != null) 1 else 0
        val hint = stream.sizeHint()
        val upper = hint.upper?.let { if (it > Int.MAX_VALUE - pendingLen) null else it + pendingLen }
        return SizeHint(0, upper)
    }
}
