// port-lint: source stream/try_stream/try_all.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [tryAll] method.
 */
@HiddenFromObjC
public class TryAll<T, E>(
    private val stream: TryStream<T, E>,
    private val predicate: (T) -> Future<Boolean>,
) : FusedFuture<Try<Boolean, E>> {
    private var pendingFuture: Future<Boolean>? = null
    private var done: Boolean = false

    public companion object {
        internal fun <T, E> new(
            stream: TryStream<T, E>,
            predicate: (T) -> Future<Boolean>,
        ): TryAll<T, E> = TryAll(stream, predicate)
    }

    override fun isTerminated(): Boolean = done && pendingFuture == null

    override fun poll(context: TaskContext): Poll<Try<Boolean, E>> {
        while (true) {
            val fut = pendingFuture
            if (fut != null) {
                when (val p = fut.poll(context)) {
                    is Poll.Ready -> {
                        pendingFuture = null
                        if (!p.value) {
                            done = true
                            return Poll.Ready(Try.Ok(false))
                        }
                    }
                    Poll.Pending -> return Poll.Pending
                }
            } else if (!done) {
                when (val p = stream.tryPollNext(context)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> {
                                when (val res = y.value) {
                                    is Try.Ok -> pendingFuture = predicate(res.value)
                                    is Try.Err -> {
                                        done = true
                                        return Poll.Ready(Try.Err(res.error))
                                    }
                                }
                            }
                            Yield.End -> {
                                done = true
                                return Poll.Ready(Try.Ok(true))
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
