// port-lint: source sink/send_all.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.sink

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.Sink
import io.github.kotlinmania.futures.SinkOutcome
import io.github.kotlinmania.futures.Stream
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import io.github.kotlinmania.futures.Yield
import kotlin.native.HiddenFromObjC

/**
 * Future for the [sendAll] method.
 */
@HiddenFromObjC
public class SendAll<Item, E>(
    private val sink: Sink<Item, E>,
    private val stream: Stream<Try<Item, E>>,
) : Future<Try<Unit, E>> {
    private var buffered: Item? = null
    private var streamExhausted: Boolean = false

    private fun tryStartSend(context: TaskContext, item: Item): Poll<SinkOutcome<E>> {
        check(buffered == null)
        return when (val p = sink.pollReady(context)) {
            is Poll.Pending -> {
                buffered = item
                Poll.pending()
            }
            is Poll.Ready -> {
                when (val outcome = p.value) {
                    is SinkOutcome.Err -> Poll.ready(outcome)
                    SinkOutcome.Ready -> Poll.ready(sink.startSend(item))
                }
            }
        }
    }

    override fun poll(context: TaskContext): Poll<Try<Unit, E>> {
        val item = buffered
        if (item != null) {
            buffered = null
            when (val p = tryStartSend(context, item)) {
                is Poll.Pending -> return Poll.pending()
                is Poll.Ready -> {
                    val outcome = p.value
                    if (outcome is SinkOutcome.Err) {
                        return Poll.ready(Try.err(outcome.error))
                    }
                }
            }
        }

        while (!streamExhausted) {
            when (val itemPoll = stream.pollNext(context)) {
                is Poll.Pending -> {
                    when (val flushPoll = sink.pollFlush(context)) {
                        is Poll.Pending -> return Poll.pending()
                        is Poll.Ready -> {
                            when (val outcome = flushPoll.value) {
                                is SinkOutcome.Err -> return Poll.ready(Try.err(outcome.error))
                                SinkOutcome.Ready -> return Poll.pending()
                            }
                        }
                    }
                }
                is Poll.Ready -> {
                    when (val y = itemPoll.value) {
                        Yield.End -> {
                            streamExhausted = true
                            break
                        }
                        is Yield.Value -> {
                            when (val tryVal = y.value) {
                                is Try.Err -> return Poll.ready(Try.err(tryVal.error))
                                is Try.Ok -> {
                                    when (val sendPoll = tryStartSend(context, tryVal.value)) {
                                        is Poll.Pending -> return Poll.pending()
                                        is Poll.Ready -> {
                                            val sendOutcome = sendPoll.value
                                            if (sendOutcome is SinkOutcome.Err) {
                                                return Poll.ready(Try.err(sendOutcome.error))
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

        return when (val flushPoll = sink.pollFlush(context)) {
            is Poll.Pending -> Poll.pending()
            is Poll.Ready -> {
                when (val outcome = flushPoll.value) {
                    is SinkOutcome.Err -> Poll.ready(Try.err(outcome.error))
                    SinkOutcome.Ready -> Poll.ready(Try.ok(Unit))
                }
            }
        }
    }

    public companion object {
        public fun <Item, E> new(sink: Sink<Item, E>, stream: Stream<Try<Item, E>>): SendAll<Item, E> =
            SendAll(sink, stream)
    }
}

/**
 * A future that completes after the given stream has been fully processed into the sink, including flushing.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.sendAll(stream: Stream<Try<Item, E>>): SendAll<Item, E> =
    SendAll(this, stream)
