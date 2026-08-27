// port-lint: source futures-util/src/sink/mod.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Sink for the [drainSink] function that discards all items sent to it.
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
}

/**
 * Create a sink that will just discard all items given to it.
 */
@HiddenFromObjC
public fun <Item> drainSink(): Sink<Item, Nothing> = Drain()

/**
 * Future for the [Sink.send] method.
 */
@HiddenFromObjC
public class SendFuture<in Item, out E>(
    private val sink: Sink<Item, E>,
    private val item: Item,
) : Future<Try<Unit, E>> {
    private var itemToSend: Item? = item
    private var sent: Boolean = false

    override fun poll(context: TaskContext): Poll<Try<Unit, E>> {
        if (!sent) {
            when (val p = sink.pollReady(context)) {
                is Poll.Pending -> return Poll.pending()
                is Poll.Ready -> {
                    when (val outcome = p.value) {
                        is SinkOutcome.Err -> return Poll.ready(Try.err(outcome.error))
                        SinkOutcome.Ready -> {
                            @Suppress("UNCHECKED_CAST")
                            val toSend = itemToSend as Item
                            itemToSend = null
                            val sendOutcome = sink.startSend(toSend)
                            sent = true
                            if (sendOutcome is SinkOutcome.Err) {
                                return Poll.ready(Try.err(sendOutcome.error))
                            }
                        }
                    }
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
}

/**
 * A future that completes after the given item has been fully processed
 * into the sink, including flushing.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.send(item: Item): Future<Try<Unit, E>> =
    SendFuture(this, item)

/**
 * Future for the [Sink.feed] method.
 */
@HiddenFromObjC
public class FeedFuture<in Item, out E>(
    private val sink: Sink<Item, E>,
    private val item: Item,
) : Future<Try<Unit, E>> {
    private var pendingItem: Item? = item

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
}

/**
 * A future that completes after the given item has been received
 * by the sink without flushing.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.feed(item: Item): Future<Try<Unit, E>> =
    FeedFuture(this, item)

/**
 * Future for the [Sink.flush] method.
 */
@HiddenFromObjC
public class FlushFuture<in Item, out E>(
    private val sink: Sink<Item, E>,
) : Future<Try<Unit, E>> {
    override fun poll(context: TaskContext): Poll<Try<Unit, E>> =
        when (val p = sink.pollFlush(context)) {
            is Poll.Pending -> Poll.pending()
            is Poll.Ready -> {
                when (val outcome = p.value) {
                    is SinkOutcome.Err -> Poll.ready(Try.err(outcome.error))
                    SinkOutcome.Ready -> Poll.ready(Try.ok(Unit))
                }
            }
        }
}

/**
 * Flush the sink, processing all pending items.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.flush(): Future<Try<Unit, E>> =
    FlushFuture(this)

/**
 * Future for the [Sink.close] method.
 */
@HiddenFromObjC
public class CloseFuture<in Item, out E>(
    private val sink: Sink<Item, E>,
) : Future<Try<Unit, E>> {
    override fun poll(context: TaskContext): Poll<Try<Unit, E>> =
        when (val p = sink.pollClose(context)) {
            is Poll.Pending -> Poll.pending()
            is Poll.Ready -> {
                when (val outcome = p.value) {
                    is SinkOutcome.Err -> Poll.ready(Try.err(outcome.error))
                    SinkOutcome.Ready -> Poll.ready(Try.ok(Unit))
                }
            }
        }
}

/**
 * Close the sink.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.close(): Future<Try<Unit, E>> =
    CloseFuture(this)

/**
 * Sink for the [buffer] combinator.
 */
@HiddenFromObjC
public class BufferSink<Item, out E>(
    private val sink: Sink<Item, E>,
    private val capacity: Int,
) : Sink<Item, E> {
    private val buf: ArrayDeque<Item> = ArrayDeque(capacity.coerceAtLeast(0))

    private fun tryEmptyBuffer(context: TaskContext): Poll<SinkOutcome<E>> {
        while (buf.isNotEmpty()) {
            when (val p = sink.pollReady(context)) {
                is Poll.Pending -> return Poll.pending()
                is Poll.Ready -> {
                    when (val outcome = p.value) {
                        is SinkOutcome.Err -> return Poll.ready(outcome)
                        SinkOutcome.Ready -> {
                            val item = buf.removeFirst()
                            val sendRes = sink.startSend(item)
                            if (sendRes is SinkOutcome.Err) {
                                return Poll.ready(sendRes)
                            }
                        }
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
            Poll.Pending -> {}
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

    public fun getRef(): Sink<Item, E> = sink
}

/**
 * Adds a fixed-size buffer to the current sink.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.buffer(capacity: Int): Sink<Item, E> =
    BufferSink(this, capacity)

/**
 * Sink for the [fanout] method.
 */
@HiddenFromObjC
public class FanoutSink<Item, out E>(
    private val sink1: Sink<Item, E>,
    private val sink2: Sink<Item, E>,
) : Sink<Item, E> {
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

    public fun intoInner(): Pair<Sink<Item, E>, Sink<Item, E>> = Pair(sink1, sink2)
}

/**
 * Fanout items to multiple sinks.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.fanout(other: Sink<Item, E>): Sink<Item, E> =
    FanoutSink(this, other)

/**
 * Sink for the [sinkMapErr] method.
 */
@HiddenFromObjC
public class SinkMapErr<in Item, in E, out E2>(
    private val sink: Sink<Item, E>,
    private val transform: (E) -> E2,
) : Sink<Item, E2> {
    private fun mapOutcome(outcome: SinkOutcome<E>): SinkOutcome<E2> =
        when (outcome) {
            SinkOutcome.Ready -> SinkOutcome.Ready
            is SinkOutcome.Err -> SinkOutcome.Err(transform(outcome.error))
        }

    override fun pollReady(context: TaskContext): Poll<SinkOutcome<E2>> =
        when (val p = sink.pollReady(context)) {
            is Poll.Ready -> Poll.ready(mapOutcome(p.value))
            Poll.Pending -> Poll.pending()
        }

    override fun startSend(item: Item): SinkOutcome<E2> =
        mapOutcome(sink.startSend(item))

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<E2>> =
        when (val p = sink.pollFlush(context)) {
            is Poll.Ready -> Poll.ready(mapOutcome(p.value))
            Poll.Pending -> Poll.pending()
        }

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<E2>> =
        when (val p = sink.pollClose(context)) {
            is Poll.Ready -> Poll.ready(mapOutcome(p.value))
            Poll.Pending -> Poll.pending()
        }
}

/**
 * Transforms the error returned by the sink.
 */
@HiddenFromObjC
public fun <Item, E, E2> Sink<Item, E>.sinkMapErr(transform: (E) -> E2): Sink<Item, E2> =
    SinkMapErr(this, transform)

/**
 * Sink for the [with] method.
 */
@HiddenFromObjC
public class WithSink<Item, in InItem, out E>(
    private val sink: Sink<Item, E>,
    private val transform: (InItem) -> Future<Item>,
) : Sink<InItem, E> {
    private var pendingFuture: Future<Item>? = null

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

    public fun getRef(): Sink<Item, E> = sink
    public fun getMut(): Sink<Item, E> = sink
}

/**
 * Composes a function in front of the sink.
 */
@HiddenFromObjC
public fun <Item, InItem, E> Sink<Item, E>.with(transform: (InItem) -> Future<Item>): Sink<InItem, E> =
    WithSink(this, transform)

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

    public fun getRef(): Sink<Item, E> = sink
    public fun getMut(): Sink<Item, E> = sink
}

/**
 * Composes a function in front of the sink that produces a stream of items.
 */
@HiddenFromObjC
public fun <Item, InItem, E> Sink<Item, E>.withFlatMap(
    transform: (InItem) -> Stream<Try<Item, E>>,
): Sink<InItem, E> = WithFlatMap(this, transform)

/**
 * Future for the [sendAll] method.
 */
@HiddenFromObjC
public class SendAll<Item, E>(
    private val sink: Sink<Item, E>,
    private val stream: Stream<Try<Item, E>>,
) : Future<Try<Unit, E>> {
    private var pendingItem: Item? = null
    private var streamExhausted: Boolean = false

    override fun poll(context: TaskContext): Poll<Try<Unit, E>> {
        while (true) {
            val item = pendingItem
            if (item != null) {
                when (val readyPoll = sink.pollReady(context)) {
                    Poll.Pending -> return Poll.pending()
                    is Poll.Ready -> {
                        when (val outcome = readyPoll.value) {
                            is SinkOutcome.Err -> return Poll.ready(Try.err(outcome.error))
                            SinkOutcome.Ready -> {
                                pendingItem = null
                                when (val sendOutcome = sink.startSend(item)) {
                                    is SinkOutcome.Err -> return Poll.ready(Try.err(sendOutcome.error))
                                    SinkOutcome.Ready -> {}
                                }
                            }
                        }
                    }
                }
            } else if (!streamExhausted) {
                when (val itemPoll = stream.pollNext(context)) {
                    Poll.Pending -> return Poll.pending()
                    is Poll.Ready -> {
                        when (val y = itemPoll.value) {
                            Yield.End -> streamExhausted = true
                            is Yield.Value -> {
                                when (val tryVal = y.value) {
                                    is Try.Err -> return Poll.ready(Try.err(tryVal.error))
                                    is Try.Ok -> {
                                        pendingItem = tryVal.value
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                return when (val flushPoll = sink.pollFlush(context)) {
                    Poll.Pending -> Poll.pending()
                    is Poll.Ready -> {
                        when (val outcome = flushPoll.value) {
                            is SinkOutcome.Err -> Poll.ready(Try.err(outcome.error))
                            SinkOutcome.Ready -> Poll.ready(Try.ok(Unit))
                        }
                    }
                }
            }
        }
    }
}

/**
 * A future that completes after the given stream has been fully processed into the sink, including flushing.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.sendAll(stream: Stream<Try<Item, E>>): Future<Try<Unit, E>> =
    SendAll(this, stream)

/**
 * Wrap this sink in an [Either] sink, making it the left-hand variant.
 */
@HiddenFromObjC
public fun <Item, E, Si2 : Sink<Item, E>> Sink<Item, E>.leftSink(): Either<Sink<Item, E>, Si2> =
    Either.Left(this)

/**
 * Wrap this sink in an [Either] sink, making it the right-hand variant.
 */
@HiddenFromObjC
public fun <Item, E, Si1 : Sink<Item, E>> Sink<Item, E>.rightSink(): Either<Si1, Sink<Item, E>> =
    Either.Right(this)

/**
 * Map this sink's error to a different error type.
 */
@HiddenFromObjC
public fun <Item, E, E2> Sink<Item, E>.sinkErrInto(transform: (E) -> E2): Sink<Item, E2> =
    sinkMapErr(transform)

/**
 * A convenience method for calling [Sink.pollReady].
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.pollReadyUnpin(context: TaskContext): Poll<SinkOutcome<E>> =
    pollReady(context)

/**
 * A convenience method for calling [Sink.startSend].
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.startSendUnpin(item: Item): SinkOutcome<E> =
    startSend(item)

/**
 * A convenience method for calling [Sink.pollFlush].
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.pollFlushUnpin(context: TaskContext): Poll<SinkOutcome<E>> =
    pollFlush(context)

/**
 * A convenience method for calling [Sink.pollClose].
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.pollCloseUnpin(context: TaskContext): Poll<SinkOutcome<E>> =
    pollClose(context)

internal fun <Item, E, S : Sink<Item, E>> assertSink(sink: S): S = sink

