// port-lint: source futures-util/src/stream/try_stream/try_fold.rs
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
    private var pendingFuture: Future<Try<Acc, E>>? = null

    public companion object {
        internal fun <T, E, Acc> new(
            stream: TryStream<T, E>,
            fold: (Acc, T) -> Future<Try<Acc, E>>,
            initial: Acc,
        ): TryFold<T, E, Acc> = TryFold(stream, fold, initial)
    }

    override fun isTerminated(): Boolean =
        accum == null && pendingFuture == null

    override fun poll(context: TaskContext): Poll<Try<Acc, E>> {
        while (true) {
            val fut = pendingFuture
            if (fut != null) {
                when (val p = fut.poll(context)) {
                    is Poll.Ready -> {
                        pendingFuture = null
                        when (val res = p.value) {
                            is Try.Ok -> accum = res.value
                            is Try.Err -> {
                                accum = null
                                return Poll.Ready(Try.Err(res.error))
                            }
                        }
                    }
                    Poll.Pending -> return Poll.Pending
                }
            } else {
                val acc = accum
                if (acc != null) {
                    when (val p = stream.tryPollNext(context)) {
                        is Poll.Ready -> {
                            when (val y = p.value) {
                                is Yield.Value -> {
                                    when (val res = y.value) {
                                        is Try.Ok -> pendingFuture = fold(acc, res.value)
                                        is Try.Err -> {
                                            accum = null
                                            return Poll.Ready(Try.Err(res.error))
                                        }
                                    }
                                }
                                Yield.End -> {
                                    accum = null
                                    return Poll.Ready(Try.Ok(acc))
                                }
                            }
                        }
                        Poll.Pending -> return Poll.Pending
                    }
                } else {
                    return Poll.Pending
                }
            }
        }
    }
}
