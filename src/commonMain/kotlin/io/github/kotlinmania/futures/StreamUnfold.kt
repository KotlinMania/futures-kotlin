// port-lint: source stream/unfold.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [streamUnfold] function.
 */
@HiddenFromObjC
public class Unfold<State, Item>(
    private var state: UnfoldState<State, Future<Pair<Item, State>?>>,
    private val f: (State) -> Future<Pair<Item, State>?>,
) : FusedStream<Item> {
    override fun isTerminated(): Boolean = state is UnfoldState.Empty

    override fun pollNext(context: TaskContext): Poll<Yield<Item>> {
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
                val step = p.value
                if (step != null) {
                    val (item, nextState) = step
                    state = UnfoldState.Value(nextState)
                    Poll.ready(Yield.value(item))
                } else {
                    state = UnfoldState.Empty
                    Poll.ready(Yield.end())
                }
            }
        }
    }
}

/**
 * Creates a [Stream] from a seed and a closure returning a [Future].
 */
@HiddenFromObjC
public fun <State, Item> streamUnfold(
    init: State,
    f: (State) -> Future<Pair<Item, State>?>,
): Unfold<State, Item> = Unfold(UnfoldState.Value(init), f)
