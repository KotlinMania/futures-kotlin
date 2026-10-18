// port-lint: source futures-test/src/track_closed.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.AsyncWrite
import io.github.kotlinmania.futures.io.IoError
import io.github.kotlinmania.futures.io.IoErrorKind
import io.github.kotlinmania.futures.io.IoSlice
import kotlin.native.HiddenFromObjC

/**
 * Async wrapper that tracks whether a sink has been closed.
 */
@HiddenFromObjC
public class TrackClosedSink<Item, Error>(
    private val inner: Sink<Item, Error>,
) : Sink<Item, Error> {
    private var closed: Boolean = false

    /** Check whether this object has been closed. */
    public fun isClosed(): Boolean = closed

    /** Acquires a reference to the underlying object. */
    public fun getRef(): Sink<Item, Error> = inner

    /** Acquires a mutable reference to the underlying object. */
    public fun getMut(): Sink<Item, Error> = inner

    /** Consumes this adaptor returning the underlying object. */
    public fun intoInner(): Sink<Item, Error> = inner

    override fun pollReady(context: TaskContext): Poll<SinkOutcome<Error>> {
        check(!closed) { "Attempted to pollReady after sink was closed" }
        return inner.pollReady(context)
    }

    override fun startSend(item: Item): SinkOutcome<Error> {
        check(!closed) { "Attempted to startSend after sink was closed" }
        return inner.startSend(item)
    }

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<Error>> {
        check(!closed) { "Attempted to pollFlush after sink was closed" }
        return inner.pollFlush(context)
    }

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<Error>> {
        check(!closed) { "Attempted to pollClose after sink was closed" }
        return when (val poll = inner.pollClose(context)) {
            is Poll.Ready -> {
                closed = true
                poll
            }
            is Poll.Pending -> poll
        }
    }

    public companion object {
        public fun <Item, Error> new(inner: Sink<Item, Error>): TrackClosedSink<Item, Error> =
            TrackClosedSink(inner)
    }
}

/**
 * Async wrapper that tracks whether an [AsyncWrite] stream has been closed.
 */
@HiddenFromObjC
public class TrackClosedWrite(
    private val inner: AsyncWrite,
) : AsyncWrite {
    private var closed: Boolean = false

    /** Check whether this object has been closed. */
    public fun isClosed(): Boolean = closed

    /** Acquires a reference to the underlying object. */
    public fun getRef(): AsyncWrite = inner

    /** Acquires a mutable reference to the underlying object. */
    public fun getMut(): AsyncWrite = inner

    /** Consumes this adaptor returning the underlying object. */
    public fun intoInner(): AsyncWrite = inner

    override fun pollWrite(
        context: TaskContext,
        buf: ByteArray,
        offset: Int,
        length: Int,
    ): Poll<Try<Int, IoError>> {
        if (closed) {
            return Poll.Ready(Try.err(IoError(IoErrorKind.Other, "Attempted to write after stream was closed")))
        }
        return inner.pollWrite(context, buf, offset, length)
    }

    override fun pollFlush(context: TaskContext): Poll<Try<Unit, IoError>> {
        if (closed) {
            return Poll.Ready(Try.err(IoError(IoErrorKind.Other, "Attempted to flush after stream was closed")))
        }
        return inner.pollFlush(context)
    }

    override fun pollClose(context: TaskContext): Poll<Try<Unit, IoError>> {
        if (closed) {
            return Poll.Ready(Try.err(IoError(IoErrorKind.Other, "Attempted to close after stream was closed")))
        }
        return when (val poll = inner.pollClose(context)) {
            is Poll.Ready -> {
                if (poll.value is Try.Ok) {
                    closed = true
                }
                poll
            }
            is Poll.Pending -> poll
        }
    }

    override fun pollWriteVectored(
        context: TaskContext,
        bufs: List<IoSlice>,
    ): Poll<Try<Int, IoError>> {
        if (closed) {
            return Poll.Ready(Try.err(IoError(IoErrorKind.Other, "Attempted to write after stream was closed")))
        }
        return inner.pollWriteVectored(context, bufs)
    }

    public companion object {
        public fun new(inner: AsyncWrite): TrackClosedWrite = TrackClosedWrite(inner)
    }
}

/**
 * Wraps this sink to track whether it has been closed.
 */
@HiddenFromObjC
public fun <Item, Error> Sink<Item, Error>.trackClosed(): TrackClosedSink<Item, Error> =
    TrackClosedSink(this)

/**
 * Wraps this writer to track whether it has been closed.
 */
@HiddenFromObjC
public fun AsyncWrite.trackClosed(): TrackClosedWrite =
    TrackClosedWrite(this)
