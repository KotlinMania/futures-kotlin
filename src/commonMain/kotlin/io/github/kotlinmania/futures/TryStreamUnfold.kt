// port-lint: source stream/try_stream/try_unfold.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [tryStreamUnfold] function.
 */
@HiddenFromObjC
public class TryUnfold<State, Item, E>(
    private var state: UnfoldState<State, Future<Try<Pair<Item, State>?, E>>>,
    private val f: (State) -> Future<Try<Pair<Item, State>?, E>>,
) : FusedStream<Try<Item, E>> {
    override fun isTerminated(): Boolean = state is UnfoldState.Empty

    override fun pollNext(context: TaskContext): Poll<Yield<Try<Item, E>>> {
        val curState = state
        if (curState is UnfoldState.Value) {
            state = UnfoldState.InProgress(f(curState.value))
        }

        val inProg = state
        if (inProg !is UnfoldState.InProgress) {
            return Poll.ready(Yield.end())
        }

        return when (val p = inProg.future.poll(context)) {
            is Poll.Pending -> Poll.pending()
            is Poll.Ready -> {
                when (val res = p.value) {
                    is Try.Ok -> {
                        val step = res.value
                        if (step != null) {
                            val (item, nextState) = step
                            state = UnfoldState.Value(nextState)
                            Poll.ready(Yield.value(Try.Ok(item)))
                        } else {
                            state = UnfoldState.Empty
                            Poll.ready(Yield.end())
                        }
                    }
                    is Try.Err -> {
                        state = UnfoldState.Empty
                        Poll.ready(Yield.value(Try.Err(res.error)))
                    }
                }
            }
        }
    }
}

/**
 * Creates a [TryStream] from a seed and a closure returning a [Future].
 */
@HiddenFromObjC
public fun <State, Item, E> tryStreamUnfold(
    init: State,
    f: (State) -> Future<Try<Pair<Item, State>?, E>>,
): TryUnfold<State, Item, E> = TryUnfold(UnfoldState.Value(init), f)
