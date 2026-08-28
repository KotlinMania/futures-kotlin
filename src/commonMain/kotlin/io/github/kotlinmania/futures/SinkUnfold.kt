// port-lint: source futures-util/src/sink/unfold.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Sink for the [unfoldSink] function.
 */
@HiddenFromObjC
public class SinkUnfold<T, in Item, out E>(
    init: T,
    private val function: (T, Item) -> Future<Try<T, E>>,
) : Sink<Item, E> {
    private var state: UnfoldState<T, Future<Try<T, E>>> = UnfoldState.Value(init)

    override fun pollReady(context: TaskContext): Poll<SinkOutcome<E>> =
        pollFlush(context)

    override fun startSend(item: Item): SinkOutcome<E> {
        val value = state.takeValue()
            ?: error("startSend called without pollReady returning Ready first")
        val future = function(value, item)
        state = UnfoldState.InProgress(future)
        return SinkOutcome.ready()
    }

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<E>> {
        val inProgress = state.futureOrNull() ?: return Poll.Ready(SinkOutcome.ready())
        val pollRes = inProgress.poll(context)
        return when (pollRes) {
            is Poll.Pending -> Poll.Pending
            is Poll.Ready -> {
                when (val result = pollRes.value) {
                    is Try.Ok -> {
                        state = UnfoldState.Value(result.value)
                        Poll.Ready(SinkOutcome.ready())
                    }
                    is Try.Err -> {
                        state = UnfoldState.Empty
                        Poll.Ready(SinkOutcome.err(result.error))
                    }
                }
            }
        }
    }

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<E>> =
        pollFlush(context)
}

/**
 * Create a sink from a function which processes one item at a time.
 */
@HiddenFromObjC
public fun <T, Item, E> unfoldSink(
    init: T,
    function: (T, Item) -> Future<Try<T, E>>,
): SinkUnfold<T, Item, E> = SinkUnfold(init, function)
