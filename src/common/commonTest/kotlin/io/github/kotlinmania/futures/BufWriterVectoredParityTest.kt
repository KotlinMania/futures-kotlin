package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.AsyncWrite
import io.github.kotlinmania.futures.io.BufWriter
import io.github.kotlinmania.futures.io.IoError
import io.github.kotlinmania.futures.io.IoErrorKind
import io.github.kotlinmania.futures.io.IoSlice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BufWriterVectoredParityTest {
    private class Writer(
        scalarEvents: List<Poll<Try<Int, IoError>>>,
        private val vectorCount: Int,
    ) : AsyncWrite {
        private val scalarEvents = scalarEvents.toMutableList()
        val scalarRequests = mutableListOf<String>()
        val vectorRequests = mutableListOf<List<String>>()
        val output = StringBuilder()

        override fun pollWrite(context: TaskContext, buf: ByteArray, offset: Int, length: Int): Poll<Try<Int, IoError>> {
            scalarRequests.add(buf.copyOfRange(offset, offset + length).decodeToString())
            val event = scalarEvents.removeAt(0)
            if (event is Poll.Ready && event.value is Try.Ok) {
                val n = event.value.value
                check(n <= length)
                output.append(buf.copyOfRange(offset, offset + n).decodeToString())
            }
            if (event is Poll.Pending) context.wakeByRef()
            return event
        }

        override fun pollWriteVectored(context: TaskContext, bufs: List<IoSlice>): Poll<Try<Int, IoError>> {
            val parts = bufs.map { it.buffer.copyOfRange(it.offset, it.offset + it.length).decodeToString() }
            vectorRequests.add(parts)
            check(parts.sumOf { it.length } == vectorCount)
            parts.forEach { output.append(it) }
            return Poll.Ready(Try.Ok(vectorCount))
        }

        override fun pollFlush(context: TaskContext): Poll<Try<Unit, IoError>> = Poll.Ready(Try.Ok(Unit))

        override fun pollClose(context: TaskContext): Poll<Try<Unit, IoError>> = Poll.Ready(Try.Ok(Unit))
    }

    private fun seed(writer: Writer, capacity: Int): BufWriter<Writer> {
        val buffered = BufWriter.withCapacity(capacity, writer)
        val result = assertIs<Poll.Ready<Try<Int, IoError>>>(buffered.pollWrite(TaskContext(), "ab".encodeToByteArray())).value
        assertEquals(2, assertIs<Try.Ok<Int>>(result).value)
        return buffered
    }

    private fun result(poll: Poll<Try<Int, IoError>>): Try<Int, IoError> = assertIs<Poll.Ready<Try<Int, IoError>>>(poll).value

    @Test
    fun smallVectorsBufferEverySliceAndRespectRanges() {
        val writer = Writer(emptyList(), 0)
        val buffered = seed(writer, 8)
        val slice = IoSlice("__cd__".encodeToByteArray(), 2, 2)
        val slices = listOf(slice, IoSlice(ByteArray(0)), IoSlice("ef".encodeToByteArray()))
        assertEquals(4, assertIs<Try.Ok<Int>>(result(buffered.pollWriteVectored(TaskContext(), slices))).value)
        assertEquals("abcdef", buffered.buffer().decodeToString())
        assertEquals(2, slice.offset)
        assertEquals(2, slice.length)
        assertTrue(writer.scalarRequests.isEmpty())
        assertTrue(writer.vectorRequests.isEmpty())
    }

    @Test
    fun vectorsAtCapacityDelegateToTheInnerVectoredMethod() {
        val writer = Writer(emptyList(), 4)
        val buffered = BufWriter.withCapacity(4, writer)
        val slices = listOf(IoSlice("ab".encodeToByteArray()), IoSlice("cd".encodeToByteArray()))
        assertEquals(4, assertIs<Try.Ok<Int>>(result(buffered.pollWriteVectored(TaskContext(), slices))).value)
        assertEquals(listOf(listOf("ab", "cd")), writer.vectorRequests)
        assertTrue(writer.scalarRequests.isEmpty())
        assertEquals("abcd", writer.output.toString())
        assertEquals(0, buffered.buffer().size)
    }

    @Test
    fun pendingFlushResumesBeforeDelegatingTheUnconsumedVectors() {
        val writer = Writer(listOf(Poll.Pending, Poll.Ready(Try.Ok(2))), 4)
        val buffered = seed(writer, 4)
        val slices = listOf(IoSlice("cd".encodeToByteArray()), IoSlice("ef".encodeToByteArray()))
        assertSame(Poll.Pending, buffered.pollWriteVectored(TaskContext(), slices))
        assertEquals("ab", buffered.buffer().decodeToString())
        assertTrue(writer.vectorRequests.isEmpty())
        assertEquals(4, assertIs<Try.Ok<Int>>(result(buffered.pollWriteVectored(TaskContext(), slices))).value)
        assertEquals(listOf("ab", "ab"), writer.scalarRequests)
        assertEquals(listOf(listOf("cd", "ef")), writer.vectorRequests)
        assertEquals("abcdef", writer.output.toString())
    }

    @Test
    fun flushErrorDoesNotConsumeOrDelegateTheInputVectors() {
        val failure = IoError(IoErrorKind.Other, "failed")
        val writer = Writer(listOf(Poll.Ready(Try.Err(failure))), 4)
        val buffered = seed(writer, 4)
        val slices = listOf(IoSlice("cd".encodeToByteArray()), IoSlice("ef".encodeToByteArray()))
        assertSame(failure, assertIs<Try.Err<IoError>>(result(buffered.pollWriteVectored(TaskContext(), slices))).error)
        assertEquals("ab", buffered.buffer().decodeToString())
        assertTrue(writer.vectorRequests.isEmpty())
        assertEquals(listOf(0, 0), slices.map { it.offset })
        assertEquals(listOf(2, 2), slices.map { it.length })
    }

    @Test
    fun emptyVectorsWithSpareCapacityStayBufferedWithoutInnerCalls() {
        val writer = Writer(emptyList(), 0)
        val buffered = seed(writer, 8)
        assertEquals(0, assertIs<Try.Ok<Int>>(result(buffered.pollWriteVectored(TaskContext(), listOf(IoSlice(ByteArray(0)))))).value)
        assertEquals("ab", buffered.buffer().decodeToString())
        assertTrue(writer.scalarRequests.isEmpty())
        assertTrue(writer.vectorRequests.isEmpty())
    }

    @Test
    fun zeroCapacityDelegatesEvenAnEmptyVectorList() {
        val writer = Writer(emptyList(), 0)
        val buffered = BufWriter.withCapacity(0, writer)
        assertEquals(0, assertIs<Try.Ok<Int>>(result(buffered.pollWriteVectored(TaskContext(), emptyList()))).value)
        assertEquals(listOf(emptyList()), writer.vectorRequests)
        assertTrue(writer.scalarRequests.isEmpty())
    }
}
