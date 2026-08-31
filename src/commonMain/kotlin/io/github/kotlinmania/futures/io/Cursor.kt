// port-lint: source futures-util/src/io/cursor.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * A [Cursor] wraps an in-memory byte buffer and provides it with [AsyncSeek],
 * [AsyncRead], [AsyncBufRead], and [AsyncWrite] implementations.
 */
@HiddenFromObjC
public class Cursor(
    private var buffer: ByteArray,
) : AsyncBufRead,
    AsyncSeek,
    AsyncWrite {
    private var pos: Long = 0L

    public constructor() : this(ByteArray(0))

    public companion object {
        public fun new(buffer: ByteArray = ByteArray(0)): Cursor = Cursor(buffer)
    }

    /**
     * Gets the current position of this cursor.
     */
    public fun position(): Long = pos

    /**
     * Sets the position of this cursor.
     */
    public fun setPosition(pos: Long) {
        this.pos = pos
    }

    /**
     * Returns a copy of the underlying buffer.
     */
    public fun intoInner(): ByteArray = buffer

    /**
     * Returns the underlying buffer.
     */
    public fun getRef(): ByteArray = buffer

    /**
     * Returns a mutable reference to the underlying buffer.
     */
    public fun getMut(): ByteArray = buffer


    override fun pollRead(
        context: TaskContext,
        buf: ByteArray,
        offset: Int,
        length: Int,
    ): Poll<Try<Int, IoError>> {
        val currentPos = pos.toInt()
        if (currentPos >= buffer.size) {
            return Poll.Ready(Try.ok(0))
        }
        val available = buffer.size - currentPos
        val toRead = minOf(length, available, buf.size - offset)
        buffer.copyInto(buf, destinationOffset = offset, startIndex = currentPos, endIndex = currentPos + toRead)
        pos += toRead
        return Poll.Ready(Try.ok(toRead))
    }

    override fun pollFillBuf(context: TaskContext): Poll<Try<ByteArray, IoError>> {
        val currentPos = pos.toInt()
        if (currentPos >= buffer.size) {
            return Poll.Ready(Try.ok(ByteArray(0)))
        }
        val remaining = buffer.copyOfRange(currentPos, buffer.size)
        return Poll.Ready(Try.ok(remaining))
    }

    override fun consume(amt: Int) {
        val maxConsume = (buffer.size - pos.toInt()).coerceAtLeast(0)
        pos += minOf(amt, maxConsume)
    }

    override fun pollSeek(
        context: TaskContext,
        pos: SeekFrom,
    ): Poll<Try<Long, IoError>> {
        val newPos =
            when (pos) {
                is SeekFrom.Start -> pos.offset
                is SeekFrom.End -> buffer.size.toLong() + pos.offset
                is SeekFrom.Current -> this.pos + pos.offset
            }
        if (newPos < 0) {
            return Poll.Ready(Try.err(IoError(IoErrorKind.InvalidInput, "invalid seek to a negative or overflowing position")))
        }
        this.pos = newPos
        return Poll.Ready(Try.ok(newPos))
    }

    override fun pollWrite(
        context: TaskContext,
        buf: ByteArray,
        offset: Int,
        length: Int,
    ): Poll<Try<Int, IoError>> {
        val currentPos = pos.toInt()
        val toWrite = minOf(length, buf.size - offset)
        val requiredSize = currentPos + toWrite
        if (requiredSize > buffer.size) {
            val newBuf = ByteArray(maxOf(requiredSize, buffer.size * 2))
            buffer.copyInto(newBuf, 0, 0, buffer.size)
            buffer = newBuf
        }
        buf.copyInto(buffer, destinationOffset = currentPos, startIndex = offset, endIndex = offset + toWrite)
        pos += toWrite
        return Poll.Ready(Try.ok(toWrite))
    }

    override fun pollFlush(context: TaskContext): Poll<Try<Unit, IoError>> =
        Poll.Ready(Try.ok(Unit))

    override fun pollClose(context: TaskContext): Poll<Try<Unit, IoError>> =
        Poll.Ready(Try.ok(Unit))

    override fun toString(): String = "Cursor(pos=$pos, size=${buffer.size})"
}
