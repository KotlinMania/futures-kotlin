// port-lint: source future/try_future/try_flatten_err.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [tryFlattenErr] method.
 */
@HiddenFromObjC
public class TryFlattenErr<T, E2>(
    future: TryFuture<T, TryFuture<T, E2>>,
) : FusedFuture<Try<T, E2>> {
    private sealed interface State<out T, out E2> {
        class First<T, E2>(
            val future: TryFuture<T, TryFuture<T, E2>>,
        ) : State<T, E2>

        class Second<T, E2>(
            val future: TryFuture<T, E2>,
        ) : State<T, E2>

        object Empty : State<Nothing, Nothing>
    }

    private var state: State<T, E2> = State.First(future)

    public companion object {
        internal fun <T, E2> new(future: TryFuture<T, TryFuture<T, E2>>): TryFlattenErr<T, E2> =
            TryFlattenErr(future)
    }

    override fun isTerminated(): Boolean = state is State.Empty

    override fun poll(context: TaskContext): Poll<Try<T, E2>> {
        while (true) {
            when (val curr = state) {
                is State.First -> {
                    when (val p = curr.future.poll(context)) {
                        is Poll.Ready -> {
                            when (val res = p.value) {
                                is Try.Ok -> {
                                    state = State.Empty
                                    return Poll.ready(Try.ok(res.value))
                                }
                                is Try.Err -> {
                                    state = State.Second(res.error)
                                }
                            }
                        }
                        Poll.Pending -> return Poll.pending()
                    }
                }
                is State.Second -> {
                    when (val p = curr.future.poll(context)) {
                        is Poll.Ready -> {
                            state = State.Empty
                            return Poll.ready(p.value)
                        }
                        Poll.Pending -> return Poll.pending()
                    }
                }
                State.Empty -> {
                    error("TryFlattenErr polled after completion")
                }
            }
        }
    }
}

/**
 * Flattens the execution of this [TryFuture] when the failure output is another [TryFuture] with matching success type.
 */
@HiddenFromObjC
public fun <T, E2> TryFuture<T, TryFuture<T, E2>>.tryFlattenErr(): TryFlattenErr<T, E2> =
    TryFlattenErr.new(this)
