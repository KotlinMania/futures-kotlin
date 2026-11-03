package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.AsyncWrite
import io.github.kotlinmania.futures.io.IoError
import io.github.kotlinmania.futures.io.IoErrorKind
import io.github.kotlinmania.futures.io.IoSlice
import io.github.kotlinmania.futures.io.LineWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LineWriterVectoredParityTest {
    private class Writer(
        counts: List<Int>,
        flushes: List<Poll<Try<Unit, IoError>>> = listOf(Poll.Ready(Try.Ok(Unit))),
    ) : AsyncWrite {
        private val counts = counts.toMutableList()
        private val flushes = flushes.toMutableList()
        val vectors = mutableListOf<List<String>>()
        val output = StringBuilder()
        var flushCalls = 0
            private set

        override fun pollWrite(context: TaskContext, buf: ByteArray, offset: Int, length: Int): Poll<Try<Int, IoError>> =
            error("unexpected scalar write")

        override fun pollWriteVectored(context: TaskContext, bufs: List<IoSlice>): Poll<Try<Int, IoError>> {
            val parts = bufs.map { it.buffer.copyOfRange(it.offset, it.offset + it.length).decodeToString() }
            vectors.add(parts)
            val count = counts.removeAt(0)
            check(count <= parts.sumOf { it.length })
            var left = count
            for (part in parts) {
                val used = minOf(left, part.length)
                output.append(part.substring(0, used))
                left -= used
            }
            return Poll.Ready(Try.Ok(count))
        }

        override fun pollFlush(context: TaskContext): Poll<Try<Unit, IoError>> {
            flushCalls += 1
            val event = flushes.removeAt(0)
            if (event is Poll.Pending) context.wakeByRef()
            return event
        }

        override fun pollClose(context: TaskContext): Poll<Try<Unit, IoError>> = Poll.Ready(Try.Ok(Unit))
    }

    private fun slices(vararg text: String): List<IoSlice> = text.map { IoSlice(it.encodeToByteArray()) }

    private fun count(poll: Poll<Try<Int, IoError>>): Int = assertIs<Try.Ok<Int>>(assertIs<Poll.Ready<Try<Int, IoError>>>(poll).value).value

    @Test
    fun newlineContainingSliceIsDelegatedWholeIncludingBytesAfterNewline() {
        val inner = Writer(listOf(4))
        val writer = LineWriter.withCapacity(8, inner)
        val range = IoSlice("__ab\nX__".encodeToByteArray(), 2, 4)
        val input = listOf(IoSlice(ByteArray(0)), range, IoSlice("TAIL".encodeToByteArray()))
        assertEquals(8, count(writer.pollWriteVectored(TaskContext(), input)))
        assertEquals(listOf(listOf("", "ab\nX")), inner.vectors)
        assertEquals("ab\nX", inner.output.toString())
        assertEquals("TAIL", writer.buffer().decodeToString())
        assertEquals(2, range.offset)
        assertEquals(4, range.length)
    }

    @Test
    fun partialVectoredWriteBuffersNeitherUnwrittenLinesNorTail() {
        val inner = Writer(listOf(1))
        val writer = LineWriter.withCapacity(8, inner)
        assertEquals(1, count(writer.pollWriteVectored(TaskContext(), slices("ab\nX", "TAIL"))))
        assertEquals("a", inner.output.toString())
        assertEquals(0, writer.buffer().size)
    }

    @Test
    fun vectorsWithoutNewlinesUseTheCanonicalBufferedVectoredPath() {
        val inner = Writer(emptyList())
        val writer = LineWriter.withCapacity(8, inner)
        assertEquals(4, count(writer.pollWriteVectored(TaskContext(), slices("ab", "", "cd"))))
        assertEquals("abcd", writer.buffer().decodeToString())
        assertTrue(inner.vectors.isEmpty())
        assertEquals(0, inner.flushCalls)
    }

    @Test
    fun tailBufferingContinuesAcrossSlicesUntilCapacityIsFull() {
        val inner = Writer(listOf(2))
        val writer = LineWriter.withCapacity(4, inner)
        assertEquals(6, count(writer.pollWriteVectored(TaskContext(), slices("a\n", "bc", "", "def", "g"))))
        assertEquals(listOf(listOf("a\n")), inner.vectors)
        assertEquals("bcde", writer.buffer().decodeToString())
    }

    @Test
    fun pendingFlushPreservesVectorsAndZeroWriteBuffersNothing() {
        val inner = Writer(listOf(0), listOf(Poll.Pending, Poll.Ready(Try.Ok(Unit))))
        val writer = LineWriter.withCapacity(8, inner)
        val input = slices("a\n", "TAIL")
        assertSame(Poll.Pending, writer.pollWriteVectored(TaskContext(), input))
        assertTrue(inner.vectors.isEmpty())
        assertEquals(0, count(writer.pollWriteVectored(TaskContext(), input)))
        assertEquals(0, writer.buffer().size)
        assertEquals(2, inner.flushCalls)
    }

    @Test
    fun flushErrorHasPriorityOverAnyVectoredWrite() {
        val failure = IoError(IoErrorKind.TimedOut, "timeout")
        val inner = Writer(emptyList(), listOf(Poll.Ready(Try.Err(failure))))
        val writer = LineWriter.withCapacity(8, inner)
        val result = assertIs<Poll.Ready<Try<Int, IoError>>>(writer.pollWriteVectored(TaskContext(), slices("a\n", "TAIL"))).value
        assertSame(failure, assertIs<Try.Err<IoError>>(result).error)
        assertTrue(inner.vectors.isEmpty())
        assertEquals(0, writer.buffer().size)
    }
}
