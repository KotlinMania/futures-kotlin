// port-lint: source stream/try_stream/try_fold.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [tryFold] method.
 */
@HiddenFromObjC
public class TryFold<T, E, Acc>(
    private val stream: TryStream<T, E>,
    private val fold: (Acc, T) -> Future<Try<Acc, E>>,
    initial: Acc,
) : FusedFuture<Try<Acc, E>> {
    private var accum: Acc? = initial
    private var hasAccum = true
    private var pendingFuture: Future<Try<Acc, E>>? = null

    public companion object {
        internal fun <T, E, Acc> new(
            stream: TryStream<T, E>,
            fold: (Acc, T) -> Future<Try<Acc, E>>,
            initial: Acc,
        ): TryFold<T, E, Acc> = TryFold(stream, fold, initial)
    }

    override fun isTerminated(): Boolean =
        !hasAccum && pendingFuture == null

    @Suppress("UNCHECKED_CAST")
    override fun poll(context: TaskContext): Poll<Try<Acc, E>> {
        while (true) {
            val future = pendingFuture
            if (future != null) {
                when (val polled = future.poll(context)) {
                    is Poll.Ready -> {
                        pendingFuture = null
                        when (val result = polled.value) {
                            is Try.Ok -> {
                                accum = result.value
                                hasAccum = true
                            }

                            is Try.Err -> {
                                return Poll.Ready(Try.Err(result.error))
                            }
                        }
                    }

                    Poll.Pending -> {
                        return Poll.Pending
                    }
                }
            } else if (hasAccum) {
                when (val polled = stream.tryPollNext(context)) {
                    is Poll.Ready -> {
                        val current = accum as Acc
                        accum = null
                        hasAccum = false
                        when (val item = polled.value) {
                            is Yield.Value -> {
                                when (val result = item.value) {
                                    is Try.Ok -> {
                                        pendingFuture = fold(current, result.value)
                                    }

                                    is Try.Err -> {
                                        return Poll.Ready(Try.Err(result.error))
                                    }
                                }
                            }

                            Yield.End -> {
                                return Poll.Ready(Try.Ok(current))
                            }
                        }
                    }

                    Poll.Pending -> {
                        return Poll.Pending
                    }
                }
            } else {
                throw RuntimeException("Fold polled after completion")
            }
        }
    }
}
