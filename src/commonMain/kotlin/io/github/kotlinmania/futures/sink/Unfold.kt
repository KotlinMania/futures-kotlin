// port-lint: source sink/unfold.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.sink

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.Sink
import io.github.kotlinmania.futures.SinkOutcome
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import io.github.kotlinmania.futures.UnfoldState
import kotlin.native.HiddenFromObjC

/**
 * Sink for the [unfold] function.
 */
@HiddenFromObjC
public class Unfold<T, in Item, out E>(
    init: T,
    private val function: (T, Item) -> Future<Try<T, E>>,
) : Sink<Item, E> {
    private var state: UnfoldState<T, Future<Try<T, E>>> = UnfoldState.Value(init)

    override fun pollReady(context: TaskContext): Poll<SinkOutcome<E>> =
        pollFlush(context)

    override fun startSend(item: Item): SinkOutcome<E> {
        val value =
            state.takeValue()
                ?: error("startSend called without pollReady returning Ready first")
        val future = function(value, item)
        state = UnfoldState.InProgress(future)
        return SinkOutcome.ready()
    }

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<E>> {
        val inProgress = state.futureOrNull() ?: return Poll.ready(SinkOutcome.ready())
        val pollRes = inProgress.poll(context)
        return when (pollRes) {
            is Poll.Pending -> Poll.pending()
            is Poll.Ready -> {
                when (val result = pollRes.value) {
                    is Try.Ok -> {
                        state = UnfoldState.Value(result.value)
                        Poll.ready(SinkOutcome.ready())
                    }
                    is Try.Err -> {
                        state = UnfoldState.Empty
                        Poll.ready(SinkOutcome.err(result.error))
                    }
                }
            }
        }
    }

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<E>> =
        pollFlush(context)

    override fun toString(): String = "Unfold"

    public companion object {
        public fun <T, Item, E> new(
            init: T,
            function: (T, Item) -> Future<Try<T, E>>,
        ): Unfold<T, Item, E> = Unfold(init, function)
    }
}

/**
 * Create a sink from a function which processes one item at a time.
 */
@HiddenFromObjC
public fun <T, Item, E> unfold(
    init: T,
    function: (T, Item) -> Future<Try<T, E>>,
): Unfold<T, Item, E> = Unfold(init, function)
