package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.AsyncWrite
import io.github.kotlinmania.futures.io.IoError
import io.github.kotlinmania.futures.io.LineWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LineWriterPartialParityTest {
    private class Writer(
        counts: List<Int>,
    ) : AsyncWrite {
        private val counts = counts.toMutableList()
        val output = StringBuilder()
        val requests = mutableListOf<String>()
        var flushes = 0
            private set

        override fun pollWrite(context: TaskContext, buf: ByteArray, offset: Int, length: Int): Poll<Try<Int, IoError>> {
            requests.add(buf.copyOfRange(offset, offset + length).decodeToString())
            val count = counts.removeAt(0)
            check(count <= length)
            output.append(buf.copyOfRange(offset, offset + count).decodeToString())
            return Poll.Ready(Try.Ok(count))
        }

        override fun pollFlush(context: TaskContext): Poll<Try<Unit, IoError>> {
            flushes += 1
            return Poll.Ready(Try.Ok(Unit))
        }

        override fun pollClose(context: TaskContext): Poll<Try<Unit, IoError>> = Poll.Ready(Try.Ok(Unit))
    }

    private fun count(poll: Poll<Try<Int, IoError>>): Int = assertIs<Try.Ok<Int>>(assertIs<Poll.Ready<Try<Int, IoError>>>(poll).value).value

    @Test
    fun shortCompletedLineBuffersOnlyItsUnwrittenRemainder() {
        val inner = Writer(listOf(1, 2))
        val writer = LineWriter.withCapacity(8, inner)
        assertEquals(3, count(writer.pollWrite(TaskContext(), "ab\nTAIL".encodeToByteArray())))
        assertEquals("b\n", writer.buffer().decodeToString())
        assertEquals("a", inner.output.toString())
        assertEquals(1, count(writer.pollWrite(TaskContext(), "z".encodeToByteArray())))
        assertEquals("ab\n", inner.output.toString())
        assertEquals("z", writer.buffer().decodeToString())
        assertEquals(1, inner.flushes)
    }

    @Test
    fun longCompletedLineUsesLastNewlineWithinTheCapacityWindow() {
        val inner = Writer(listOf(1))
        val writer = LineWriter.withCapacity(4, inner)
        assertEquals(4, count(writer.pollWrite(TaskContext(), "a\nb\nc\nTAIL".encodeToByteArray())))
        assertEquals("\nb\n", writer.buffer().decodeToString())
        assertEquals(listOf("a\nb\nc\n"), inner.requests)
    }

    @Test
    fun inputOffsetDoesNotIncludeTheFollowingTailOrOutsideBytes() {
        val inner = Writer(listOf(1))
        val writer = LineWriter.withCapacity(8, inner)
        assertEquals(3, count(writer.pollWrite(TaskContext(), "__ab\nTAIL__".encodeToByteArray(), 2, 7)))
        assertEquals("b\n", writer.buffer().decodeToString())
        assertEquals(listOf("ab\n"), inner.requests)
    }

    @Test
    fun capacityWindowWithoutNewlineBuffersTheWholeWindow() {
        val inner = Writer(listOf(1))
        val writer = LineWriter.withCapacity(4, inner)
        assertEquals(5, count(writer.pollWrite(TaskContext(), "abcdef\nTAIL".encodeToByteArray())))
        assertEquals("bcde", writer.buffer().decodeToString())
    }

    @Test
    fun fullyWrittenCompletedLineCanBufferItsFollowingTail() {
        val inner = Writer(listOf(3))
        val writer = LineWriter.withCapacity(8, inner)
        assertEquals(7, count(writer.pollWrite(TaskContext(), "ab\nTAIL".encodeToByteArray())))
        assertEquals("TAIL", writer.buffer().decodeToString())
        assertEquals("ab\n", inner.output.toString())
    }

    @Test
    fun zeroInnerWriteDoesNotBufferAnyInput() {
        val inner = Writer(listOf(0))
        val writer = LineWriter.withCapacity(8, inner)
        assertEquals(0, count(writer.pollWrite(TaskContext(), "ab\nTAIL".encodeToByteArray())))
        assertEquals(0, writer.buffer().size)
    }
}
