// port-lint: source stream/try_stream/try_for_each.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [tryForEach] method.
 */
@HiddenFromObjC
public class TryStreamForEach<T, E>(
    private val stream: TryStream<T, E>,
    private val action: (T) -> Future<Try<Unit, E>>,
) : FusedFuture<Try<Unit, E>> {
    private var pendingFuture: Future<Try<Unit, E>>? = null
    private var done: Boolean = false

    public companion object {
        internal fun <T, E> new(
            stream: TryStream<T, E>,
            action: (T) -> Future<Try<Unit, E>>,
        ): TryStreamForEach<T, E> = TryStreamForEach(stream, action)
    }

    override fun isTerminated(): Boolean = done

    override fun poll(context: TaskContext): Poll<Try<Unit, E>> {
        while (true) {
            val fut = pendingFuture
            if (fut != null) {
                when (val p = fut.poll(context)) {
                    is Poll.Ready -> {
                        pendingFuture = null
                        when (val res = p.value) {
                            is Try.Ok -> {}
                            is Try.Err -> {
                                done = true
                                return Poll.Ready(Try.Err(res.error))
                            }
                        }
                    }
                    Poll.Pending -> return Poll.Pending
                }
            } else {
                when (val p = stream.tryPollNext(context)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> {
                                when (val res = y.value) {
                                    is Try.Ok -> pendingFuture = action(res.value)
                                    is Try.Err -> {
                                        done = true
                                        return Poll.Ready(Try.Err(res.error))
                                    }
                                }
                            }
                            Yield.End -> {
                                done = true
                                return Poll.Ready(Try.Ok(Unit))
                            }
                        }
                    }
                    Poll.Pending -> return Poll.Pending
                }
            }
        }
    }
}
