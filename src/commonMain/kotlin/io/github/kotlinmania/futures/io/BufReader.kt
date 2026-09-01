// port-lint: source io/buf_reader.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

public const val DEFAULT_BUF_SIZE: Int = 8192

/**
 * The [BufReader] adds buffering to any [AsyncRead] instance.
 */
@HiddenFromObjC
public class BufReader(
    private val inner: AsyncRead,
    capacity: Int = DEFAULT_BUF_SIZE,
) : AsyncBufRead,
    AsyncSeek {
    private val buffer: ByteArray = ByteArray(capacity)
    private var pos: Int = 0
    private var cap: Int = 0

    /**
     * Returns the internally buffered data.
     */
    public fun buffer(): ByteArray =
        if (pos < cap) buffer.copyOfRange(pos, cap) else ByteArray(0)

    override fun pollFillBuf(context: TaskContext): Poll<Try<ByteArray, IoError>> {
        if (pos >= cap) {
            val pollRes = inner.pollRead(context, buffer, 0, buffer.size)
            when (pollRes) {
                is Poll.Pending -> return Poll.Pending
                is Poll.Ready -> {
                    when (val result = pollRes.value) {
                        is Try.Err -> return Poll.Ready(Try.err(result.error))
                        is Try.Ok -> {
                            pos = 0
                            cap = result.value
                        }
                    }
                }
            }
        }
        return Poll.Ready(Try.ok(buffer.copyOfRange(pos, cap)))
    }

    override fun consume(amt: Int) {
        pos = minOf(pos + amt, cap)
    }

    override fun pollRead(
        context: TaskContext,
        buf: ByteArray,
        offset: Int,
        length: Int,
    ): Poll<Try<Int, IoError>> {
        if (pos == cap && length >= buffer.size) {
            pos = 0
            cap = 0
            return inner.pollRead(context, buf, offset, length)
        }
        val fillRes = pollFillBuf(context)
        when (fillRes) {
            is Poll.Pending -> return Poll.Pending
            is Poll.Ready -> {
                when (val result = fillRes.value) {
                    is Try.Err -> return Poll.Ready(Try.err(result.error))
                    is Try.Ok -> {
                        val available = cap - pos
                        if (available == 0) {
                            return Poll.Ready(Try.ok(0))
                        }
                        val toRead = minOf(length, available, buf.size - offset)
                        buffer.copyInto(buf, destinationOffset = offset, startIndex = pos, endIndex = pos + toRead)
                        pos += toRead
                        return Poll.Ready(Try.ok(toRead))
                    }
                }
            }
        }
    }

    override fun pollSeek(
        context: TaskContext,
        pos: SeekFrom,
    ): Poll<Try<Long, IoError>> {
        if (inner is AsyncSeek) {
            this.pos = 0
            this.cap = 0
            return inner.pollSeek(context, pos)
        }
        return Poll.Ready(Try.err(IoError(IoErrorKind.Other, "inner reader does not support AsyncSeek")))
    }
}
