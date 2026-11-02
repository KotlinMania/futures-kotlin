// port-lint: source io/line_writer.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Wrap a writer, like [BufWriter] does, but prioritizes buffering lines.
 */
@HiddenFromObjC
public class LineWriter<out W : AsyncWrite>(
    public val inner: W,
    capacity: Int = 1024,
) : AsyncWrite {
    private val bufWriter: BufWriter<W> = BufWriter(inner, capacity)

    public companion object {
        public fun <W : AsyncWrite> new(inner: W): LineWriter<W> = LineWriter(inner)

        public fun <W : AsyncWrite> withCapacity(capacity: Int, inner: W): LineWriter<W> =
            LineWriter(inner, capacity)
    }

    /**
     * Gets a reference to the underlying writer.
     */
    public fun getRef(): W = inner

    /**
     * Gets a mutable reference to the underlying writer.
     */
    public fun getMut(): W = inner

    /**
     * Consumes this [LineWriter], returning the underlying writer.
     */
    public fun intoInner(): W = inner

    public fun fmt(): String = "LineWriter"

    override fun toString(): String = fmt()

    /**
     * Returns a copy of the internally buffered data.
     */
    public fun buffer(): ByteArray = bufWriter.buffer()

    private fun flushIfCompletedLine(context: TaskContext): Poll<Try<Unit, IoError>> {
        val currentBuf = bufWriter.buffer()
        return if (currentBuf.isNotEmpty() && currentBuf.last() == '\n'.code.toByte()) {
            bufWriter.flushBuf(context)
        } else {
            Poll.Ready(Try.ok(Unit))
        }
    }

    override fun pollWrite(
        context: TaskContext,
        buf: ByteArray,
        offset: Int,
        length: Int,
    ): Poll<Try<Int, IoError>> {
        var lastNewline = -1
        val end = offset + length
        for (i in (end - 1) downTo offset) {
            if (buf[i] == '\n'.code.toByte()) {
                lastNewline = i
                break
            }
        }

        if (lastNewline == -1) {
            val flushLine = flushIfCompletedLine(context)
            when (flushLine) {
                is Poll.Pending -> return Poll.Pending
                is Poll.Ready -> {
                    val resVal = flushLine.value
                    if (resVal is Try.Err) {
                        return Poll.Ready(Try.err(resVal.error))
                    }
                }
            }
            return bufWriter.pollWrite(context, buf, offset, length)
        }

        val newlineIndex = (lastNewline - offset) + 1

        val flushRes = bufWriter.pollFlush(context)
        when (flushRes) {
            is Poll.Pending -> return Poll.Pending
            is Poll.Ready -> {
                val resVal = flushRes.value
                if (resVal is Try.Err) {
                    return Poll.Ready(Try.err(resVal.error))
                }
            }
        }

        val linesLen = newlineIndex
        val pollInner = bufWriter.innerPollWrite(context, buf, offset, linesLen)
        val flushed =
            when (pollInner) {
                is Poll.Pending -> return Poll.Pending
                is Poll.Ready -> {
                    when (val r = pollInner.value) {
                        is Try.Err -> return Poll.Ready(Try.err(r.error))
                        is Try.Ok -> r.value
                    }
                }
            }

        if (flushed == 0) {
            return Poll.Ready(Try.ok(0))
        }

        val tailOffset = offset + flushed
        val tailEnd =
            when {
                flushed >= newlineIndex -> {
                    offset + length
                }

                newlineIndex - flushed <= bufWriter.capacity() -> {
                    offset + newlineIndex
                }

                else -> {
                    val scanEnd = tailOffset + bufWriter.capacity()
                    var lastNewlineInWindow = -1
                    for (i in (scanEnd - 1) downTo tailOffset) {
                        if (buf[i] == '\n'.code.toByte()) {
                            lastNewlineInWindow = i
                            break
                        }
                    }
                    if (lastNewlineInWindow >= 0) lastNewlineInWindow + 1 else scanEnd
                }
            }
        val buffered = bufWriter.writeToBuf(buf, tailOffset, tailEnd - tailOffset)

        return Poll.Ready(Try.ok(flushed + buffered))
    }

    override fun pollWriteVectored(
        context: TaskContext,
        bufs: List<IoSlice>,
    ): Poll<Try<Int, IoError>> {
        val lastNewlineBuffer =
            bufs.indexOfLast { slice ->
                (slice.offset until slice.offset + slice.length).any { slice.buffer[it] == '\n'.code.toByte() }
            }
        if (lastNewlineBuffer < 0) {
            when (val flushed = flushIfCompletedLine(context)) {
                Poll.Pending -> {
                    return Poll.Pending
                }

                is Poll.Ready -> {
                    val result = flushed.value
                    if (result is Try.Err) return Poll.Ready(result)
                }
            }
            return bufWriter.pollWriteVectored(context, bufs)
        }
        when (val flushed = bufWriter.pollFlush(context)) {
            Poll.Pending -> {
                return Poll.Pending
            }

            is Poll.Ready -> {
                val result = flushed.value
                if (result is Try.Err) return Poll.Ready(result)
            }
        }
        val lines = bufs.subList(0, lastNewlineBuffer + 1)
        val flushed =
            when (val polled = inner.pollWriteVectored(context, lines)) {
                Poll.Pending -> {
                    return Poll.Pending
                }

                is Poll.Ready -> {
                    when (val result = polled.value) {
                        is Try.Err -> {
                            return Poll.Ready(result)
                        }

                        is Try.Ok -> {
                            result.value
                        }
                    }
                }
            }
        if (flushed == 0) return Poll.Ready(Try.Ok(0))
        val linesLength = lines.sumOf { it.length.toLong() }
        if (flushed.toLong() < linesLength) return Poll.Ready(Try.Ok(flushed))
        var buffered = 0
        for (i in lastNewlineBuffer + 1 until bufs.size) {
            val slice = bufs[i]
            if (slice.isEmpty) continue
            val copied = bufWriter.writeToBuf(slice.buffer, slice.offset, slice.length)
            if (copied == 0) break
            buffered += copied
        }
        return Poll.Ready(Try.Ok(flushed + buffered))
    }

    override fun pollFlush(context: TaskContext): Poll<Try<Unit, IoError>> =
        bufWriter.pollFlush(context)

    override fun pollClose(context: TaskContext): Poll<Try<Unit, IoError>> =
        bufWriter.pollClose(context)
}
