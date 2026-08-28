// port-lint: source futures-util/src/io/line_writer.rs
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
        val flushed = when (pollInner) {
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
        val tailLen = length - flushed
        val buffered = if (tailLen > 0) {
            bufWriter.writeToBuf(buf, tailOffset, tailLen)
        } else {
            0
        }

        return Poll.Ready(Try.ok(flushed + buffered))
    }

    override fun pollFlush(context: TaskContext): Poll<Try<Unit, IoError>> =
        bufWriter.pollFlush(context)

    override fun pollClose(context: TaskContext): Poll<Try<Unit, IoError>> =
        bufWriter.pollClose(context)
}
