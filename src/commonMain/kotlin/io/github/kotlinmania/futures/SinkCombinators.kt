@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

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
