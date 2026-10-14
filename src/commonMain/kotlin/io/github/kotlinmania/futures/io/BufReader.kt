// port-lint: source io/buf_reader.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
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

    public companion object {
        public fun new(inner: AsyncRead): BufReader = BufReader(inner, DEFAULT_BUF_SIZE)

        public fun withCapacity(capacity: Int, inner: AsyncRead): BufReader = BufReader(inner, capacity)
    }

    public fun getRef(): AsyncRead = inner

    public fun getMut(): AsyncRead = inner

    public fun getPinMut(): AsyncRead = inner

    public fun intoInner(): AsyncRead = inner

    /**
     * Returns the internally buffered data.
     */
    public fun buffer(): ByteArray =
        if (pos < cap) buffer.copyOfRange(pos, cap) else ByteArray(0)

    public fun discardBuffer() {
        pos = 0
        cap = 0
    }

    public fun seekRelative(offset: Long): SeeKRelative =
        SeeKRelative(this, offset)

    public fun pollSeekRelative(
        context: TaskContext,
        offset: Long,
    ): Poll<Try<Unit, IoError>> {
        val currentPos = pos.toULong()
        if (offset < 0L) {
            val magnitude = 0uL - offset.toULong()
            if (currentPos >= magnitude) {
                pos = (currentPos - magnitude).toInt()
                return Poll.Ready(Try.ok(Unit))
            }
        } else {
            val uOffset = offset.toULong()
            if (ULong.MAX_VALUE - currentPos >= uOffset) {
                val newPos = currentPos + uOffset
                if (newPos <= cap.toULong()) {
                    pos = newPos.toInt()
                    return Poll.Ready(Try.ok(Unit))
                }
            }
        }
        return when (val seekRes = pollSeek(context, SeekFrom.Current(offset))) {
            is Poll.Pending -> Poll.Pending
            is Poll.Ready ->
                when (val result = seekRes.value) {
                    is Try.Ok -> Poll.Ready(Try.ok(Unit))
                    is Try.Err -> Poll.Ready(Try.err(result.error))
                }
        }
    }

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
        if (amt <= 0) {
            return
        }
        pos = if (cap - pos < amt) cap else pos + amt
    }

    override fun pollRead(
        context: TaskContext,
        buf: ByteArray,
        offset: Int,
        length: Int,
    ): Poll<Try<Int, IoError>> {
        if (pos == cap && length >= buffer.size) {
            val res = inner.pollRead(context, buf, offset, length)
            if (res is Poll.Pending) {
                return Poll.Pending
            }
            discardBuffer()
            return res
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
    ): Poll<Try<ULong, IoError>> {
        val seeker =
            inner as? AsyncSeek
                ?: return Poll.Ready(Try.err(IoError(IoErrorKind.Other, "inner reader does not support AsyncSeek")))

        val result: ULong
        if (pos is SeekFrom.Current) {
            val n = pos.offset
            val remainder = (cap - this.pos).toLong()
            if (remainder == 0L || n >= Long.MIN_VALUE + remainder) {
                val offset = n - remainder
                when (val pollRes = seeker.pollSeek(context, SeekFrom.Current(offset))) {
                    is Poll.Pending -> return Poll.Pending
                    is Poll.Ready ->
                        when (val innerRes = pollRes.value) {
                            is Try.Err -> return Poll.Ready(Try.err(innerRes.error))
                            is Try.Ok -> result = innerRes.value
                        }
                }
            } else {
                when (val backRes = seeker.pollSeek(context, SeekFrom.Current(-remainder))) {
                    is Poll.Pending -> return Poll.Pending
                    is Poll.Ready ->
                        when (val innerBack = backRes.value) {
                            is Try.Err -> return Poll.Ready(Try.err(innerBack.error))
                            is Try.Ok -> {}
                        }
                }
                discardBuffer()
                when (val fwdRes = seeker.pollSeek(context, SeekFrom.Current(n))) {
                    is Poll.Pending -> return Poll.Pending
                    is Poll.Ready ->
                        when (val innerFwd = fwdRes.value) {
                            is Try.Err -> return Poll.Ready(Try.err(innerFwd.error))
                            is Try.Ok -> result = innerFwd.value
                        }
                }
            }
        } else {
            when (val pollRes = seeker.pollSeek(context, pos)) {
                is Poll.Pending -> return Poll.Pending
                is Poll.Ready ->
                    when (val innerRes = pollRes.value) {
                        is Try.Err -> return Poll.Ready(Try.err(innerRes.error))
                        is Try.Ok -> result = innerRes.value
                    }
            }
        }
        discardBuffer()
        return Poll.Ready(Try.ok(result))
    }
}

/**
 * Future for the [BufReader.seekRelative] method.
 */
@HiddenFromObjC
public class SeeKRelative(
    private val inner: BufReader,
    private val offset: Long,
    private var first: Boolean = true,
) : Future<Try<Unit, IoError>> {
    override fun poll(context: TaskContext): Poll<Try<Unit, IoError>> =
        if (first) {
            first = false
            inner.pollSeekRelative(context, offset)
        } else {
            when (val seekRes = inner.pollSeek(context, SeekFrom.Current(offset))) {
                is Poll.Pending -> Poll.Pending
                is Poll.Ready ->
                    when (val result = seekRes.value) {
                        is Try.Ok -> Poll.Ready(Try.ok(Unit))
                        is Try.Err -> Poll.Ready(Try.err(result.error))
                    }
            }
        }
}
