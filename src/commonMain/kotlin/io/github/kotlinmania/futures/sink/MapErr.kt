// port-lint: source sink/map_err.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.sink

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.Sink
import io.github.kotlinmania.futures.SinkOutcome
import io.github.kotlinmania.futures.TaskContext
import kotlin.native.HiddenFromObjC

/**
 * Sink for the [sinkMapErr] method.
 */
@HiddenFromObjC
public class SinkMapErr<in Item, E, out E2>(
    private val sink: Sink<Item, E>,
    private val transform: (E) -> E2,
) : Sink<Item, E2> {
    /**
     * Get a reference to the inner sink.
     */
    public fun getRef(): Sink<Item, E> = sink

    private fun mapOutcome(outcome: SinkOutcome<E>): SinkOutcome<E2> =
        when (outcome) {
            SinkOutcome.Ready -> SinkOutcome.Ready
            is SinkOutcome.Err -> SinkOutcome.Err(transform(outcome.error))
        }

    override fun pollReady(context: TaskContext): Poll<SinkOutcome<E2>> =
        when (val p = sink.pollReady(context)) {
            is Poll.Ready -> Poll.ready(mapOutcome(p.value))
            Poll.Pending -> Poll.pending()
        }

    override fun startSend(item: Item): SinkOutcome<E2> =
        mapOutcome(sink.startSend(item))

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<E2>> =
        when (val p = sink.pollFlush(context)) {
            is Poll.Ready -> Poll.ready(mapOutcome(p.value))
            Poll.Pending -> Poll.pending()
        }

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<E2>> =
        when (val p = sink.pollClose(context)) {
            is Poll.Ready -> Poll.ready(mapOutcome(p.value))
            Poll.Pending -> Poll.pending()
        }

    override fun toString(): String = "SinkMapErr(sink=$sink)"

    public companion object {
        public fun <Item, E, E2> new(sink: Sink<Item, E>, transform: (E) -> E2): SinkMapErr<Item, E, E2> =
            SinkMapErr(sink, transform)
    }
}

/**
 * Transforms the error returned by the sink.
 */
@HiddenFromObjC
public fun <Item, E, E2> Sink<Item, E>.sinkMapErr(transform: (E) -> E2): SinkMapErr<Item, E, E2> =
    SinkMapErr(this, transform)
