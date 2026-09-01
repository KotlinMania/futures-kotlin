// port-lint: source sink/mod.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.sink.Buffer
import io.github.kotlinmania.futures.sink.Close
import io.github.kotlinmania.futures.sink.Drain
import io.github.kotlinmania.futures.sink.Fanout
import io.github.kotlinmania.futures.sink.Feed
import io.github.kotlinmania.futures.sink.Flush
import io.github.kotlinmania.futures.sink.Send
import io.github.kotlinmania.futures.sink.SendAll
import io.github.kotlinmania.futures.sink.SinkErrInto
import io.github.kotlinmania.futures.sink.SinkMapErr
import io.github.kotlinmania.futures.sink.With
import io.github.kotlinmania.futures.sink.WithFlatMap
import io.github.kotlinmania.futures.sink.buffer as sinkBuffer
import io.github.kotlinmania.futures.sink.close as sinkClose
import io.github.kotlinmania.futures.sink.drain as sinkDrain
import io.github.kotlinmania.futures.sink.fanout as sinkFanout
import io.github.kotlinmania.futures.sink.feed as sinkFeed
import io.github.kotlinmania.futures.sink.flush as sinkFlush
import io.github.kotlinmania.futures.sink.send as sinkSend
import io.github.kotlinmania.futures.sink.sendAll as sinkSendAll
import io.github.kotlinmania.futures.sink.sinkErrInto as sinkErrIntoExt
import io.github.kotlinmania.futures.sink.sinkMapErr as sinkMapErrExt
import io.github.kotlinmania.futures.sink.with as sinkWith
import io.github.kotlinmania.futures.sink.withFlatMap as sinkWithFlatMap
import kotlin.native.HiddenFromObjC

/**
 * Sink for the [drainSink] function that discards all items sent to it.
 */
public typealias Drain<Item> = Drain<Item>

/**
 * Create a sink that will just discard all items given to it.
 */
@HiddenFromObjC
public fun <Item> drainSink(): Drain<Item> = sinkDrain()

/**
 * Future for the [Sink.send] method.
 */
public typealias SendFuture<Item, E> = Send<Item, E>

/**
 * A future that completes after the given item has been fully processed
 * into the sink, including flushing.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.send(item: Item): Send<Item, E> =
    sinkSend(item)

/**
 * Future for the [Sink.feed] method.
 */
public typealias FeedFuture<Item, E> = Feed<Item, E>

/**
 * A future that completes after the given item has been received
 * by the sink without flushing.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.feed(item: Item): Feed<Item, E> =
    sinkFeed(item)

/**
 * Future for the [Sink.flush] method.
 */
public typealias FlushFuture<Item, E> = Flush<Item, E>

/**
 * Flush the sink, processing all pending items.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.flush(): Flush<Item, E> =
    sinkFlush()

/**
 * Future for the [Sink.close] method.
 */
public typealias CloseFuture<Item, E> = Close<Item, E>

/**
 * Close the sink.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.close(): Close<Item, E> =
    sinkClose()

/**
 * Sink for the [buffer] combinator.
 */
public typealias BufferSink<Item, E> = Buffer<Item, E>

/**
 * Adds a fixed-size buffer to the current sink.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.buffer(capacity: Int): Buffer<Item, E> =
    sinkBuffer(capacity)

/**
 * Sink for the [fanout] method.
 */
public typealias FanoutSink<Item, E> = Fanout<Item, E>

/**
 * Fanout items to multiple sinks.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.fanout(other: Sink<Item, E>): Fanout<Item, E> =
    sinkFanout(other)

/**
 * Sink for the [sinkMapErr] method.
 */
public typealias SinkMapErr<Item, E, E2> = SinkMapErr<Item, E, E2>

/**
 * Transforms the error returned by the sink.
 */
@HiddenFromObjC
public fun <Item, E, E2> Sink<Item, E>.sinkMapErr(transform: (E) -> E2): SinkMapErr<Item, E, E2> =
    sinkMapErrExt(transform)

/**
 * Sink for the [with] method.
 */
public typealias WithSink<Item, InItem, E> = With<Item, InItem, E>

/**
 * Composes a function in front of the sink.
 */
@HiddenFromObjC
public fun <Item, InItem, E> Sink<Item, E>.with(transform: (InItem) -> Future<Item>): With<Item, InItem, E> =
    sinkWith(transform)

/**
 * Sink for the [withFlatMap] method.
 */
public typealias WithFlatMap<Item, InItem, E> = WithFlatMap<Item, InItem, E>

/**
 * Composes a function in front of the sink that produces a stream of items.
 */
@HiddenFromObjC
public fun <Item, InItem, E> Sink<Item, E>.withFlatMap(
    transform: (InItem) -> Stream<Try<Item, E>>,
): WithFlatMap<Item, InItem, E> = sinkWithFlatMap(transform)

/**
 * Future for the [sendAll] method.
 */
public typealias SendAll<Item, E> = SendAll<Item, E>

/**
 * A future that completes after the given stream has been fully processed into the sink, including flushing.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.sendAll(stream: Stream<Try<Item, E>>): SendAll<Item, E> =
    sinkSendAll(stream)

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
public fun <Item, E, E2> Sink<Item, E>.sinkErrInto(transform: (E) -> E2): SinkErrInto<Item, E, E2> =
    sinkErrIntoExt(transform)

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
