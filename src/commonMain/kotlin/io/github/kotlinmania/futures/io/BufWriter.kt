// port-lint: source futures-util/src/io/buf_writer.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Wraps a writer and buffers its output.
 */
@HiddenFromObjC
public class BufWriter<out W : AsyncWrite>(
    public val inner: W,
    capacity: Int = DEFAULT_BUF_SIZE,
) : AsyncWrite,
    AsyncRead,
    AsyncBufRead,
    AsyncSeek {
    private val buf: ByteArray = ByteArray(capacity)
    private var bufLen: Int = 0
    private var written: Int = 0

    public companion object {
        public fun <W : AsyncWrite> new(inner: W): BufWriter<W> = BufWriter(inner)

        public fun <W : AsyncWrite> withCapacity(capacity: Int, inner: W): BufWriter<W> =
            BufWriter(inner, capacity)
    }

    /**
     * Returns a copy of the internally buffered data.
     */
    public fun buffer(): ByteArray =
        if (bufLen > 0) buf.copyOfRange(0, bufLen) else ByteArray(0)

    public fun fmt(): String = "BufWriter"

    override fun toString(): String = fmt()


    /**
     * Capacity of the internal buffer.
     */
    public fun capacity(): Int = buf.size

    /**
     * Remaining number of bytes to reach the buffer's capacity.
     */
    public fun spareCapacity(): Int = buf.size - bufLen

    /**
     * Write byte slice directly into the internal buffer, up to [spareCapacity].
     */
    public fun writeToBuf(src: ByteArray, offset: Int = 0, length: Int = src.size - offset): Int {
        val available = spareCapacity()
        val amtToBuffer = minOf(available, length)
        if (amtToBuffer > 0) {
            src.copyInto(buf, destinationOffset = bufLen, startIndex = offset, endIndex = offset + amtToBuffer)
            bufLen += amtToBuffer
        }
        return amtToBuffer
    }

    /**
     * Flush internal buffer into the underlying writer.
     */
    public fun flushBuf(context: TaskContext): Poll<Try<Unit, IoError>> {
        while (written < bufLen) {
            val pollRes = inner.pollWrite(context, buf, written, bufLen - written)
            when (pollRes) {
                is Poll.Pending -> return Poll.Pending
                is Poll.Ready -> {
                    when (val result = pollRes.value) {
                        is Try.Err -> return Poll.Ready(Try.err(result.error))
                        is Try.Ok -> {
                            val n = result.value
                            if (n == 0) {
                                return Poll.Ready(
                                    Try.err(
                                        IoError(
                                            IoErrorKind.WriteZero,
                                            "failed to write the buffered data",
                                        ),
                                    ),
                                )
                            }
                            written += n
                        }
                    }
                }
            }
        }
        if (written > 0) {
            if (written < bufLen) {
                buf.copyInto(buf, destinationOffset = 0, startIndex = written, endIndex = bufLen)
                bufLen -= written
            } else {
                bufLen = 0
            }
        }
        written = 0
        return Poll.Ready(Try.ok(Unit))
    }

    /**
     * Write directly using [inner], bypassing buffering.
     */
    public fun innerPollWrite(
        context: TaskContext,
        src: ByteArray,
        offset: Int = 0,
        length: Int = src.size - offset,
    ): Poll<Try<Int, IoError>> = inner.pollWrite(context, src, offset, length)

    override fun pollWrite(
        context: TaskContext,
        buf: ByteArray,
        offset: Int,
        length: Int,
    ): Poll<Try<Int, IoError>> {
        if (bufLen + length > this.buf.size) {
            val flushRes = flushBuf(context)
            when (flushRes) {
                is Poll.Pending -> return Poll.Pending
                is Poll.Ready -> {
                    val resVal = flushRes.value
                    if (resVal is Try.Err) {
                        return Poll.Ready(Try.err(resVal.error))
                    }
                }
            }
        }
        return if (length >= this.buf.size) {
            inner.pollWrite(context, buf, offset, length)
        } else {
            buf.copyInto(this.buf, destinationOffset = bufLen, startIndex = offset, endIndex = offset + length)
            bufLen += length
            Poll.Ready(Try.ok(length))
        }
    }

    override fun pollFlush(context: TaskContext): Poll<Try<Unit, IoError>> {
        val flushRes = flushBuf(context)
        when (flushRes) {
            is Poll.Pending -> return Poll.Pending
            is Poll.Ready -> {
                if (flushRes.value is Try.Err) {
                    return Poll.Ready(flushRes.value)
                }
            }
        }
        return inner.pollFlush(context)
    }

    override fun pollClose(context: TaskContext): Poll<Try<Unit, IoError>> {
        val flushRes = flushBuf(context)
        when (flushRes) {
            is Poll.Pending -> return Poll.Pending
            is Poll.Ready -> {
                if (flushRes.value is Try.Err) {
                    return Poll.Ready(flushRes.value)
                }
            }
        }
        return inner.pollClose(context)
    }

    override fun pollRead(
        context: TaskContext,
        buf: ByteArray,
        offset: Int,
        length: Int,
    ): Poll<Try<Int, IoError>> {
        val reader = inner as? AsyncRead
            ?: return Poll.Ready(Try.err(IoError(IoErrorKind.Other, "inner writer does not implement AsyncRead")))
        return reader.pollRead(context, buf, offset, length)
    }

    override fun pollFillBuf(context: TaskContext): Poll<Try<ByteArray, IoError>> {
        val bufReader = inner as? AsyncBufRead
            ?: return Poll.Ready(Try.err(IoError(IoErrorKind.Other, "inner writer does not implement AsyncBufRead")))
        return bufReader.pollFillBuf(context)
    }

    override fun consume(amt: Int) {
        (inner as? AsyncBufRead)?.consume(amt)
    }

    override fun pollSeek(
        context: TaskContext,
        pos: SeekFrom,
    ): Poll<Try<Long, IoError>> {
        val flushRes = flushBuf(context)
        when (flushRes) {
            is Poll.Pending -> return Poll.Pending
            is Poll.Ready -> {
                val resVal = flushRes.value
                if (resVal is Try.Err) {
                    return Poll.Ready(Try.err(resVal.error))
                }
            }
        }
        val seeker = inner as? AsyncSeek
            ?: return Poll.Ready(Try.err(IoError(IoErrorKind.Other, "inner writer does not implement AsyncSeek")))
        return seeker.pollSeek(context, pos)
    }
}
