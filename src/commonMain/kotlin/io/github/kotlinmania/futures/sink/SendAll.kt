// port-lint: source sink/send_all.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.sink

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.Sink
import io.github.kotlinmania.futures.SinkOutcome
import io.github.kotlinmania.futures.Stream
import io.github.kotlinmania.futures.StreamFuse
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
    stream: Stream<Try<Item, E>>,
) : Future<Try<Unit, E>> {
    private val stream: StreamFuse<Try<Item, E>> = StreamFuse.new(stream)
    private var buffered: Try.Ok<Item>? = null

    private fun tryStartSend(context: TaskContext, item: Item): Poll<SinkOutcome<E>> {
        check(buffered == null)
        return when (val p = sink.pollReady(context)) {
            is Poll.Pending -> {
                buffered = Try.Ok(item)
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
        // Write a previously buffered item before doing anything else.
        val item = buffered
        if (item != null) {
            buffered = null
            when (val result = tryStartSend(context, item.value)) {
                is Poll.Pending -> {
                    return Poll.Pending
                }

                is Poll.Ready -> {
                    val outcome = result.value
                    if (outcome is SinkOutcome.Err) return Poll.Ready(Try.Err(outcome.error))
                }
            }
        }

        while (true) {
            when (val result = stream.pollNext(context)) {
                is Poll.Pending -> {
                    when (val flush = sink.pollFlush(context)) {
                        is Poll.Pending -> {
                            return Poll.Pending
                        }

                        is Poll.Ready -> {
                            return when (val outcome = flush.value) {
                                is SinkOutcome.Err -> Poll.Ready(Try.Err(outcome.error))
                                SinkOutcome.Ready -> Poll.Pending
                            }
                        }
                    }
                }

                is Poll.Ready -> {
                    when (val item = result.value) {
                        is Yield.Value -> {
                            when (val outcome = item.value) {
                                is Try.Err -> {
                                    return Poll.Ready(Try.Err(outcome.error))
                                }

                                is Try.Ok -> {
                                    when (val send = tryStartSend(context, outcome.value)) {
                                        is Poll.Pending -> {
                                            return Poll.Pending
                                        }

                                        is Poll.Ready -> {
                                            val sendOutcome = send.value
                                            if (sendOutcome is SinkOutcome.Err) return Poll.Ready(Try.Err(sendOutcome.error))
                                        }
                                    }
                                }
                            }
                        }

                        Yield.End -> {
                            return when (val flush = sink.pollFlush(context)) {
                                is Poll.Pending -> {
                                    Poll.Pending
                                }

                                is Poll.Ready -> {
                                    when (val outcome = flush.value) {
                                        is SinkOutcome.Err -> Poll.Ready(Try.Err(outcome.error))
                                        SinkOutcome.Ready -> Poll.Ready(Try.Ok(Unit))
                                    }
                                }
                            }
                        }
                    }
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
