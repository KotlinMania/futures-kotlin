// port-lint: source sink/drain.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.sink

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.Sink
import io.github.kotlinmania.futures.SinkOutcome
import io.github.kotlinmania.futures.TaskContext
import kotlin.native.HiddenFromObjC

/**
 * Sink for the [drain] function that discards all items sent to it.
 */
@HiddenFromObjC
public class Drain<in Item> : Sink<Item, Nothing> {
    override fun pollReady(context: TaskContext): Poll<SinkOutcome<Nothing>> =
        Poll.ready(SinkOutcome.ready())

    override fun startSend(item: Item): SinkOutcome<Nothing> =
        SinkOutcome.ready()

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<Nothing>> =
        Poll.ready(SinkOutcome.ready())

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<Nothing>> =
        Poll.ready(SinkOutcome.ready())

    override fun toString(): String = "Drain"
}

/**
 * Create a sink that will just discard all items given to it.
 */
@HiddenFromObjC
public fun <Item> drain(): Drain<Item> = Drain()
