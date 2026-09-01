// port-lint: source sink/fanout.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.sink

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.Sink
import io.github.kotlinmania.futures.SinkOutcome
import io.github.kotlinmania.futures.TaskContext
import kotlin.native.HiddenFromObjC

/**
 * Sink that forwards items to two sinks at the same time.
 *
 * Backpressure from any downstream sink propagates up, which means that this sink
 * can only process items as fast as its slowest downstream sink.
 */
@HiddenFromObjC
public class Fanout<Item, out E>(
    private val sink1: Sink<Item, E>,
    private val sink2: Sink<Item, E>,
) : Sink<Item, E> {
    /**
     * Get a reference to the inner sinks.
     */
    public fun getRef(): Pair<Sink<Item, E>, Sink<Item, E>> =
        Pair(sink1, sink2)

    /**
     * Consumes this combinator, returning the underlying sinks.
     */
    public fun intoInner(): Pair<Sink<Item, E>, Sink<Item, E>> =
        Pair(sink1, sink2)

    override fun pollReady(context: TaskContext): Poll<SinkOutcome<E>> {
        val r1 = sink1.pollReady(context)
        val r2 = sink2.pollReady(context)
        if (r1 is Poll.Ready && r1.value is SinkOutcome.Err) return r1
        if (r2 is Poll.Ready && r2.value is SinkOutcome.Err) return r2
        return if (r1 is Poll.Ready && r2 is Poll.Ready) {
            Poll.ready(SinkOutcome.ready())
        } else {
            Poll.pending()
        }
    }

    override fun startSend(item: Item): SinkOutcome<E> {
        val s1 = sink1.startSend(item)
        if (s1 is SinkOutcome.Err) return s1
        return sink2.startSend(item)
    }

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<E>> {
        val r1 = sink1.pollFlush(context)
        val r2 = sink2.pollFlush(context)
        if (r1 is Poll.Ready && r1.value is SinkOutcome.Err) return r1
        if (r2 is Poll.Ready && r2.value is SinkOutcome.Err) return r2
        return if (r1 is Poll.Ready && r2 is Poll.Ready) {
            Poll.ready(SinkOutcome.ready())
        } else {
            Poll.pending()
        }
    }

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<E>> {
        val r1 = sink1.pollClose(context)
        val r2 = sink2.pollClose(context)
        if (r1 is Poll.Ready && r1.value is SinkOutcome.Err) return r1
        if (r2 is Poll.Ready && r2.value is SinkOutcome.Err) return r2
        return if (r1 is Poll.Ready && r2 is Poll.Ready) {
            Poll.ready(SinkOutcome.ready())
        } else {
            Poll.pending()
        }
    }

    override fun toString(): String = "Fanout(sink1=$sink1, sink2=$sink2)"

    public companion object {
        public fun <Item, E> new(sink1: Sink<Item, E>, sink2: Sink<Item, E>): Fanout<Item, E> =
            Fanout(sink1, sink2)
    }
}

/**
 * Fanout items to multiple sinks.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.fanout(other: Sink<Item, E>): Fanout<Item, E> =
    Fanout(this, other)
