// port-lint: source io/cursor.rs
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
    private var pos: ULong = 0uL

    public constructor() : this(ByteArray(0))

    public companion object {
        public fun new(buffer: ByteArray = ByteArray(0)): Cursor = Cursor(buffer)
    }

    /**
     * Gets the current position of this cursor.
     */
    public fun position(): ULong = pos

    /**
     * Sets the position of this cursor.
     */
    public fun setPosition(pos: ULong) {
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
        if (pos >= buffer.size.toULong() || length <= 0) {
            return Poll.Ready(Try.ok(0))
        }
        val currentPos = pos.toInt()
        val available = buffer.size - currentPos
        val toRead = minOf(length, available, buf.size - offset)
        if (toRead <= 0) {
            return Poll.Ready(Try.ok(0))
        }
        buffer.copyInto(buf, destinationOffset = offset, startIndex = currentPos, endIndex = currentPos + toRead)
        pos += toRead.toULong()
        return Poll.Ready(Try.ok(toRead))
    }

    override fun pollFillBuf(context: TaskContext): Poll<Try<ByteArray, IoError>> {
        if (pos >= buffer.size.toULong()) {
            return Poll.Ready(Try.ok(ByteArray(0)))
        }
        val currentPos = pos.toInt()
        val remaining = buffer.copyOfRange(currentPos, buffer.size)
        return Poll.Ready(Try.ok(remaining))
    }

    override fun consume(amt: Int) {
        if (amt <= 0) {
            return
        }
        val delta = amt.toULong()
        pos = if (ULong.MAX_VALUE - pos < delta) ULong.MAX_VALUE else pos + delta
    }

    override fun pollSeek(
        context: TaskContext,
        pos: SeekFrom,
    ): Poll<Try<ULong, IoError>> {
        val newPos: ULong? =
            when (pos) {
                is SeekFrom.Start -> pos.offset
                is SeekFrom.End -> checkedAddSigned(buffer.size.toULong(), pos.offset)
                is SeekFrom.Current -> checkedAddSigned(this.pos, pos.offset)
            }
        if (newPos == null) {
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
        val toWrite = minOf(length, buf.size - offset)
        if (toWrite <= 0) {
            return Poll.Ready(Try.ok(0))
        }
        if (pos > Int.MAX_VALUE.toULong() - toWrite.toULong()) {
            return Poll.Ready(Try.err(IoError(IoErrorKind.InvalidInput, "cursor position exceeds maximum buffer size")))
        }
        val currentPos = pos.toInt()
        val requiredSize = currentPos + toWrite
        if (requiredSize > buffer.size) {
            val newBuf = ByteArray(requiredSize)
            buffer.copyInto(newBuf, 0, 0, buffer.size)
            buffer = newBuf
        }
        buf.copyInto(buffer, destinationOffset = currentPos, startIndex = offset, endIndex = offset + toWrite)
        pos = requiredSize.toULong()
        return Poll.Ready(Try.ok(toWrite))
    }

    override fun pollFlush(context: TaskContext): Poll<Try<Unit, IoError>> =
        Poll.Ready(Try.ok(Unit))

    override fun pollClose(context: TaskContext): Poll<Try<Unit, IoError>> =
        Poll.Ready(Try.ok(Unit))

    override fun toString(): String = "Cursor(pos=$pos, size=${buffer.size})"
}

private fun checkedAddSigned(base: ULong, delta: Long): ULong? =
    if (delta >= 0L) {
        val uDelta = delta.toULong()
        if (ULong.MAX_VALUE - base < uDelta) null else base + uDelta
    } else {
        val magnitude = 0uL - delta.toULong()
        if (base < magnitude) null else base - magnitude
    }
