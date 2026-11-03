// port-lint: source sink/send.rs
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
 * Future for the [send] method.
 */
@HiddenFromObjC
public class Send<in Item, out E>(
    private val sink: Sink<Item, E>,
    item: Item,
) : Future<Try<Unit, E>> {
    private val feed: Feed<Item, E> = Feed(sink, item)

    override fun poll(context: TaskContext): Poll<Try<Unit, E>> {
        if (feed.isItemPending()) {
            when (val p = feed.poll(context)) {
                is Poll.Pending -> return Poll.pending()
                is Poll.Ready -> {
                    if (p.value is Try.Err) return p
                }
            }
        }
        return when (val p = sink.pollFlush(context)) {
            is Poll.Pending -> Poll.pending()
            is Poll.Ready -> {
                when (val outcome = p.value) {
                    is SinkOutcome.Err -> Poll.ready(Try.err(outcome.error))
                    SinkOutcome.Ready -> Poll.ready(Try.ok(Unit))
                }
            }
        }
    }

    public companion object {
        public fun <Item, E> new(sink: Sink<Item, E>, item: Item): Send<Item, E> =
            Send(sink, item)
    }
}

/**
 * A future that completes after the given item has been fully processed
 * into the sink, including flushing.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.send(item: Item): Send<Item, E> =
    Send(this, item)
