// port-lint: source sink/feed.rs
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
 * Future for the [feed] method.
 */
@HiddenFromObjC
public class Feed<Item, out E>(
    private val sink: Sink<Item, E>,
    item: Item,
) : Future<Try<Unit, E>> {
    private var pendingItem: Item? = item

    /**
     * Returns true if the item is still pending to be sent.
     */
    public fun isItemPending(): Boolean = pendingItem != null

    override fun poll(context: TaskContext): Poll<Try<Unit, E>> {
        val current = pendingItem ?: return Poll.ready(Try.ok(Unit))
        when (val p = sink.pollReady(context)) {
            is Poll.Pending -> return Poll.pending()
            is Poll.Ready -> {
                when (val outcome = p.value) {
                    is SinkOutcome.Err -> return Poll.ready(Try.err(outcome.error))
                    SinkOutcome.Ready -> {
                        pendingItem = null
                        return when (val sendOutcome = sink.startSend(current)) {
                            SinkOutcome.Ready -> Poll.ready(Try.ok(Unit))
                            is SinkOutcome.Err -> Poll.ready(Try.err(sendOutcome.error))
                        }
                    }
                }
            }
        }
    }

    public companion object {
        public fun <Item, E> new(sink: Sink<Item, E>, item: Item): Feed<Item, E> =
            Feed(sink, item)
    }
}

/**
 * Feeds an item into this sink, if possible.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.feed(item: Item): Feed<Item, E> =
    Feed(this, item)
