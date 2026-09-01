// port-lint: source sink/with.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.sink

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.Sink
import io.github.kotlinmania.futures.SinkOutcome
import io.github.kotlinmania.futures.TaskContext
import kotlin.native.HiddenFromObjC

/**
 * Sink for the [with] method.
 */
@HiddenFromObjC
public class With<Item, in InItem, out E>(
    private val sink: Sink<Item, E>,
    private val transform: (InItem) -> Future<Item>,
) : Sink<InItem, E> {
    private var pendingFuture: Future<Item>? = null

    /**
     * Get a reference to the inner sink.
     */
    public fun getRef(): Sink<Item, E> = sink

    /**
     * Get a mutable reference to the inner sink.
     */
    public fun getMut(): Sink<Item, E> = sink

    private fun completePending(context: TaskContext): Poll<SinkOutcome<E>> {
        val fut = pendingFuture ?: return Poll.ready(SinkOutcome.ready())
        return when (val p = fut.poll(context)) {
            Poll.Pending -> Poll.pending()
            is Poll.Ready -> {
                pendingFuture = null
                when (val readyPoll = sink.pollReady(context)) {
                    Poll.Pending -> Poll.pending()
                    is Poll.Ready -> {
                        if (readyPoll.value is SinkOutcome.Err) return readyPoll
                        val sendRes = sink.startSend(p.value)
                        Poll.ready(sendRes)
                    }
                }
            }
        }
    }

    override fun pollReady(context: TaskContext): Poll<SinkOutcome<E>> {
        when (val res = completePending(context)) {
            Poll.Pending -> return Poll.pending()
            is Poll.Ready -> {
                if (res.value is SinkOutcome.Err) return res
            }
        }
        return sink.pollReady(context)
    }

    override fun startSend(item: InItem): SinkOutcome<E> {
        pendingFuture = transform(item)
        return SinkOutcome.ready()
    }

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<E>> {
        when (val res = completePending(context)) {
            Poll.Pending -> return Poll.pending()
            is Poll.Ready -> {
                if (res.value is SinkOutcome.Err) return res
            }
        }
        return sink.pollFlush(context)
    }

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<E>> {
        when (val res = completePending(context)) {
            Poll.Pending -> return Poll.pending()
            is Poll.Ready -> {
                if (res.value is SinkOutcome.Err) return res
            }
        }
        return sink.pollClose(context)
    }

    override fun toString(): String = "With(sink=$sink)"

    public companion object {
        public fun <Item, InItem, E> new(
            sink: Sink<Item, E>,
            transform: (InItem) -> Future<Item>,
        ): With<Item, InItem, E> = With(sink, transform)
    }
}

/**
 * Composes a function in front of the sink.
 */
@HiddenFromObjC
public fun <Item, InItem, E> Sink<Item, E>.with(transform: (InItem) -> Future<Item>): With<Item, InItem, E> =
    With(this, transform)
