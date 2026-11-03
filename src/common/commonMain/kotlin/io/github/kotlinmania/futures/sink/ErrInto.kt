// port-lint: source sink/err_into.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.sink

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.Sink
import io.github.kotlinmania.futures.SinkOutcome
import io.github.kotlinmania.futures.TaskContext
import kotlin.native.HiddenFromObjC

/**
 * Sink for the [sinkErrInto] method.
 */
@HiddenFromObjC
public class SinkErrInto<in Item, E, out E2>(
    sink: Sink<Item, E>,
    transform: (E) -> E2,
) : Sink<Item, E2> {
    private val mapped: SinkMapErr<Item, E, E2> = SinkMapErr.new(sink, transform)

    /**
     * Get a reference to the inner sink.
     */
    public fun getRef(): Sink<Item, E> = mapped.getRef()

    override fun pollReady(context: TaskContext): Poll<SinkOutcome<E2>> =
        mapped.pollReady(context)

    override fun startSend(item: Item): SinkOutcome<E2> =
        mapped.startSend(item)

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<E2>> =
        mapped.pollFlush(context)

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<E2>> =
        mapped.pollClose(context)

    override fun toString(): String = "SinkErrInto(sink=${mapped.getRef()})"

    public companion object {
        public fun <Item, E, E2> new(sink: Sink<Item, E>, transform: (E) -> E2): SinkErrInto<Item, E, E2> =
            SinkErrInto(sink, transform)
    }
}

/**
 * Map this sink's error to a different error type.
 */
@HiddenFromObjC
public fun <Item, E, E2> Sink<Item, E>.sinkErrInto(transform: (E) -> E2): SinkErrInto<Item, E, E2> =
    SinkErrInto(this, transform)
