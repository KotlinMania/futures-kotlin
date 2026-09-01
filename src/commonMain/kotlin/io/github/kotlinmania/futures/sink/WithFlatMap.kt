// port-lint: source sink/with_flat_map.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.sink

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.Sink
import io.github.kotlinmania.futures.SinkOutcome
import io.github.kotlinmania.futures.Stream
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import io.github.kotlinmania.futures.Yield
import kotlin.native.HiddenFromObjC

/**
 * Sink for the [withFlatMap] method.
 */
@HiddenFromObjC
public class WithFlatMap<Item, in InItem, out E>(
    private val sink: Sink<Item, E>,
    private val transform: (InItem) -> Stream<Try<Item, E>>,
) : Sink<InItem, E> {
    private var currentStream: Stream<Try<Item, E>>? = null
    private var pendingItem: Item? = null

    /**
     * Get a reference to the inner sink.
     */
    public fun getRef(): Sink<Item, E> = sink

    /**
     * Get a mutable reference to the inner sink.
     */
    public fun getMut(): Sink<Item, E> = sink

    private fun tryDrain(context: TaskContext): Poll<SinkOutcome<E>> {
        while (true) {
            val item = pendingItem
            if (item != null) {
                when (val readyPoll = sink.pollReady(context)) {
                    Poll.Pending -> return Poll.pending()
                    is Poll.Ready -> {
                        when (val outcome = readyPoll.value) {
                            is SinkOutcome.Err -> return Poll.ready(outcome)
                            SinkOutcome.Ready -> {
                                pendingItem = null
                                val sendOutcome = sink.startSend(item)
                                if (sendOutcome is SinkOutcome.Err) {
                                    return Poll.ready(sendOutcome)
                                }
                            }
                        }
                    }
                }
            }
            val st = currentStream ?: return Poll.ready(SinkOutcome.ready())
            when (val itemPoll = st.pollNext(context)) {
                Poll.Pending -> return Poll.pending()
                is Poll.Ready -> {
                    when (val y = itemPoll.value) {
                        Yield.End -> {
                            currentStream = null
                            return Poll.ready(SinkOutcome.ready())
                        }
                        is Yield.Value -> {
                            when (val t = y.value) {
                                is Try.Err -> return Poll.ready(SinkOutcome.err(t.error))
                                is Try.Ok -> {
                                    pendingItem = t.value
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun pollReady(context: TaskContext): Poll<SinkOutcome<E>> {
        when (val drainPoll = tryDrain(context)) {
            Poll.Pending -> return Poll.pending()
            is Poll.Ready -> {
                if (drainPoll.value is SinkOutcome.Err) return drainPoll
            }
        }
        return if (currentStream != null || pendingItem != null) {
            Poll.pending()
        } else {
            sink.pollReady(context)
        }
    }

    override fun startSend(item: InItem): SinkOutcome<E> {
        currentStream = transform(item)
        return SinkOutcome.ready()
    }

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<E>> {
        when (val drainPoll = tryDrain(context)) {
            Poll.Pending -> return Poll.pending()
            is Poll.Ready -> {
                if (drainPoll.value is SinkOutcome.Err) return drainPoll
            }
        }
        return sink.pollFlush(context)
    }

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<E>> {
        when (val drainPoll = tryDrain(context)) {
            Poll.Pending -> return Poll.pending()
            is Poll.Ready -> {
                if (drainPoll.value is SinkOutcome.Err) return drainPoll
            }
        }
        return sink.pollClose(context)
    }

    override fun toString(): String = "WithFlatMap(sink=$sink)"

    public companion object {
        public fun <Item, InItem, E> new(
            sink: Sink<Item, E>,
            transform: (InItem) -> Stream<Try<Item, E>>,
        ): WithFlatMap<Item, InItem, E> = WithFlatMap(sink, transform)
    }
}

/**
 * Composes a function in front of the sink that produces a stream of items.
 */
@HiddenFromObjC
public fun <Item, InItem, E> Sink<Item, E>.withFlatMap(
    transform: (InItem) -> Stream<Try<Item, E>>,
): WithFlatMap<Item, InItem, E> = WithFlatMap(this, transform)
