// port-lint: source sink/close.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.sink

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.Sink
import io.github.kotlinmania.futures.SinkOutcome
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Future for the [close] method.
 */
@HiddenFromObjC
public class Close<in Item, out E>(
    private val sink: Sink<Item, E>,
) : Future<Try<Unit, E>> {
    override fun poll(context: TaskContext): Poll<Try<Unit, E>> =
        when (val p = sink.pollClose(context)) {
            is Poll.Pending -> Poll.pending()
            is Poll.Ready -> {
                when (val outcome = p.value) {
                    is SinkOutcome.Err -> Poll.ready(Try.err(outcome.error))
                    SinkOutcome.Ready -> Poll.ready(Try.ok(Unit))
                }
            }
        }

    public companion object {
        public fun <Item, E> new(sink: Sink<Item, E>): Close<Item, E> = Close(sink)
    }
}

/**
 * Close the sink.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.close(): Close<Item, E> =
    Close(this)
