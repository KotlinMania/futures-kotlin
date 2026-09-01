// port-lint: source stream/stream/forward.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [forward] method.
 */
@HiddenFromObjC
public class Forward<Item, E>(
    stream: Stream<Try<Item, E>>,
    private val sink: Sink<Item, E>?,
) : FusedFuture<Try<Unit, E>> {
    private var activeSink: Sink<Item, E>? = sink
    private val stream: StreamFuse<Try<Item, E>> = StreamFuse.new(stream)
    private var bufferedItem: Item? = null

    public companion object {
        internal fun <Item, E> new(
            stream: Stream<Try<Item, E>>,
            sink: Sink<Item, E>,
        ): Forward<Item, E> = Forward(stream, sink)
    }

    override fun isTerminated(): Boolean = activeSink == null

    override fun poll(context: TaskContext): Poll<Try<Unit, E>> {
        val si = activeSink ?: error("polled `Forward` after completion")

        while (true) {
            val buffered = bufferedItem
            if (buffered != null) {
                when (val readyPoll = si.pollReady(context)) {
                    Poll.Pending -> return Poll.pending()
                    is Poll.Ready -> {
                        when (val outcome = readyPoll.value) {
                            is SinkOutcome.Err -> return Poll.ready(Try.err(outcome.error))
                            SinkOutcome.Ready -> {
                                bufferedItem = null
                                val sendOutcome = si.startSend(buffered)
                                if (sendOutcome is SinkOutcome.Err) {
                                    return Poll.ready(Try.err(sendOutcome.error))
                                }
                            }
                        }
                    }
                }
            }

            when (val streamPoll = stream.pollNext(context)) {
                Poll.Pending -> {
                    when (val flushPoll = si.pollFlush(context)) {
                        Poll.Pending -> return Poll.pending()
                        is Poll.Ready -> {
                            when (val outcome = flushPoll.value) {
                                is SinkOutcome.Err -> return Poll.ready(Try.err(outcome.error))
                                SinkOutcome.Ready -> return Poll.pending()
                            }
                        }
                    }
                }
                is Poll.Ready -> {
                    when (val y = streamPoll.value) {
                        is Yield.Value -> {
                            when (val tryVal = y.value) {
                                is Try.Err -> return Poll.ready(Try.err(tryVal.error))
                                is Try.Ok -> {
                                    bufferedItem = tryVal.value
                                }
                            }
                        }
                        Yield.End -> {
                            when (val closePoll = si.pollClose(context)) {
                                Poll.Pending -> return Poll.pending()
                                is Poll.Ready -> {
                                    activeSink = null
                                    return when (val outcome = closePoll.value) {
                                        is SinkOutcome.Err -> Poll.ready(Try.err(outcome.error))
                                        SinkOutcome.Ready -> Poll.ready(Try.ok(Unit))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Forwards this stream of items into the given sink, closing the sink when the stream ends.
 */
@HiddenFromObjC
public fun <Item, E> Stream<Try<Item, E>>.forward(sink: Sink<Item, E>): Forward<Item, E> =
    Forward.new(this, sink)
