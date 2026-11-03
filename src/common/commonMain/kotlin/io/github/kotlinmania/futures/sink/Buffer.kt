// port-lint: source sink/buffer.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.sink

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.Sink
import io.github.kotlinmania.futures.SinkOutcome
import io.github.kotlinmania.futures.TaskContext
import kotlin.native.HiddenFromObjC

/**
 * Sink for the [buffer] method.
 */
@HiddenFromObjC
public class Buffer<Item, out E>(
    private val sink: Sink<Item, E>,
    private val capacity: Int,
) : Sink<Item, E> {
    private val buf: ArrayDeque<Item> = ArrayDeque(capacity.coerceAtLeast(0))

    /**
     * Get a reference to the inner sink.
     */
    public fun getRef(): Sink<Item, E> = sink

    private fun tryEmptyBuffer(context: TaskContext): Poll<SinkOutcome<E>> {
        when (val readyPoll = sink.pollReady(context)) {
            is Poll.Pending -> return Poll.pending()
            is Poll.Ready -> {
                if (readyPoll.value is SinkOutcome.Err) return readyPoll
            }
        }
        while (buf.isNotEmpty()) {
            val item = buf.removeFirst()
            val sendOutcome = sink.startSend(item)
            if (sendOutcome is SinkOutcome.Err) {
                return Poll.ready(sendOutcome)
            }
            if (buf.isNotEmpty()) {
                when (val readyPoll = sink.pollReady(context)) {
                    is Poll.Pending -> return Poll.pending()
                    is Poll.Ready -> {
                        if (readyPoll.value is SinkOutcome.Err) return readyPoll
                    }
                }
            }
        }
        return Poll.ready(SinkOutcome.ready())
    }

    override fun pollReady(context: TaskContext): Poll<SinkOutcome<E>> {
        if (capacity <= 0) {
            return sink.pollReady(context)
        }
        when (val drainPoll = tryEmptyBuffer(context)) {
            is Poll.Ready -> {
                if (drainPoll.value is SinkOutcome.Err) return drainPoll
            }
            is Poll.Pending -> {}
        }
        return if (buf.size >= capacity) {
            Poll.pending()
        } else {
            Poll.ready(SinkOutcome.ready())
        }
    }

    override fun startSend(item: Item): SinkOutcome<E> =
        if (capacity <= 0) {
            sink.startSend(item)
        } else {
            buf.addLast(item)
            SinkOutcome.ready()
        }

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<E>> {
        when (val drainPoll = tryEmptyBuffer(context)) {
            is Poll.Pending -> return Poll.pending()
            is Poll.Ready -> {
                if (drainPoll.value is SinkOutcome.Err) return drainPoll
            }
        }
        return sink.pollFlush(context)
    }

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<E>> {
        when (val drainPoll = tryEmptyBuffer(context)) {
            is Poll.Pending -> return Poll.pending()
            is Poll.Ready -> {
                if (drainPoll.value is SinkOutcome.Err) return drainPoll
            }
        }
        return sink.pollClose(context)
    }

    override fun toString(): String = "Buffer(sink=$sink, capacity=$capacity)"

    public companion object {
        public fun <Item, E> new(sink: Sink<Item, E>, capacity: Int): Buffer<Item, E> =
            Buffer(sink, capacity)
    }
}

/**
 * Adds a fixed-size buffer to the current sink.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.buffer(capacity: Int): Buffer<Item, E> =
    Buffer(this, capacity)
