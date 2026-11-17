package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.AsyncWrite
import io.github.kotlinmania.futures.io.BufWriter
import io.github.kotlinmania.futures.io.IoError
import io.github.kotlinmania.futures.io.IoErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class BufWriterFlushParityTest {
    private class Writer(
        events: List<Poll<Try<Int, IoError>>>,
    ) : AsyncWrite {
        private val events = events.toMutableList()
        val output = mutableListOf<Byte>()
        val requests = mutableListOf<String>()
        var flushes = 0
            private set

        override fun pollWrite(context: TaskContext, buf: ByteArray, offset: Int, length: Int): Poll<Try<Int, IoError>> {
            requests.add(buf.copyOfRange(offset, offset + length).decodeToString())
            val event = events.removeAt(0)
            if (event is Poll.Ready && event.value is Try.Ok) {
                val n = event.value.value
                check(n <= length)
                output.addAll(buf.copyOfRange(offset, offset + n).toList())
            }
            if (event is Poll.Pending) context.wakeByRef()
            return event
        }

        override fun pollFlush(context: TaskContext): Poll<Try<Unit, IoError>> {
            flushes += 1
            return Poll.Ready(Try.Ok(Unit))
        }

        override fun pollClose(context: TaskContext): Poll<Try<Unit, IoError>> = Poll.Ready(Try.Ok(Unit))
    }

    private fun count(n: Int): Poll<Try<Int, IoError>> = Poll.Ready(Try.Ok(n))

    private fun error(e: IoError): Poll<Try<Int, IoError>> = Poll.Ready(Try.Err(e))

    private fun seeded(writer: Writer): BufWriter<Writer> {
        val buffered = BufWriter.withCapacity(4, writer)
        for (bytes in listOf("ab", "cd")) {
            val result = assertIs<Poll.Ready<Try<Int, IoError>>>(buffered.pollWrite(TaskContext(), bytes.encodeToByteArray())).value
            assertEquals(2, assertIs<Try.Ok<Int>>(result).value)
        }
        return buffered
    }

    @Test
    fun partialWriteThenErrorRemovesWrittenPrefixBeforeReturning() {
        val failure = IoError(IoErrorKind.Other, "failed")
        val writer = Writer(listOf(count(2), error(failure)))
        val buffered = seeded(writer)
        val result = assertIs<Poll.Ready<Try<Unit, IoError>>>(buffered.pollFlush(TaskContext())).value
        assertSame(failure, assertIs<Try.Err<IoError>>(result).error)
        assertEquals("cd", buffered.buffer().decodeToString())
        assertEquals(2, buffered.spareCapacity())
        assertEquals("ab", writer.output.toByteArray().decodeToString())
        assertEquals(0, writer.flushes)
    }

    @Test
    fun partialWriteThenZeroAlsoDrainsOnlyTheWrittenPrefix() {
        val writer = Writer(listOf(count(2), count(0)))
        val buffered = seeded(writer)
        val result = assertIs<Poll.Ready<Try<Unit, IoError>>>(buffered.pollFlush(TaskContext())).value
        val failure = assertIs<Try.Err<IoError>>(result).error
        assertEquals(IoErrorKind.WriteZero, failure.kind)
        assertEquals("failed to write the buffered data", failure.message)
        assertEquals("cd", buffered.buffer().decodeToString())
        assertEquals(2, buffered.spareCapacity())
    }

    @Test
    fun newWriteUsesCapacityFreedByPartialTerminalError() {
        val writer = Writer(listOf(count(2), error(IoError(IoErrorKind.Other, "failed")), Poll.Pending))
        val buffered = seeded(writer)
        assertIs<Try.Err<IoError>>(assertIs<Poll.Ready<Try<Unit, IoError>>>(buffered.pollFlush(TaskContext())).value)
        val result = assertIs<Poll.Ready<Try<Int, IoError>>>(buffered.pollWrite(TaskContext(), "ef".encodeToByteArray())).value
        assertEquals(2, assertIs<Try.Ok<Int>>(result).value)
        assertEquals("cdef", buffered.buffer().decodeToString())
        assertEquals(listOf("abcd", "cd"), writer.requests)
    }

    @Test
    fun retryAfterErrorDoesNotWriteThePrefixAgain() {
        val writer = Writer(listOf(count(2), error(IoError(IoErrorKind.Other, "failed")), count(2)))
        val buffered = seeded(writer)
        assertIs<Try.Err<IoError>>(assertIs<Poll.Ready<Try<Unit, IoError>>>(buffered.pollFlush(TaskContext())).value)
        assertIs<Try.Ok<Unit>>(assertIs<Poll.Ready<Try<Unit, IoError>>>(buffered.pollFlush(TaskContext())).value)
        assertEquals("abcd", writer.output.toByteArray().decodeToString())
        assertEquals(listOf("abcd", "cd", "cd"), writer.requests)
        assertEquals(0, buffered.buffer().size)
        assertEquals(1, writer.flushes)
    }

    @Test
    fun pendingPreservesTheCursorUntilTheNextPollCompletes() {
        val writer = Writer(listOf(count(2), Poll.Pending, count(2)))
        val buffered = seeded(writer)
        assertSame(Poll.Pending, buffered.pollFlush(TaskContext()))
        assertEquals("abcd", buffered.buffer().decodeToString())
        assertEquals(0, buffered.spareCapacity())
        assertIs<Try.Ok<Unit>>(assertIs<Poll.Ready<Try<Unit, IoError>>>(buffered.pollFlush(TaskContext())).value)
        assertEquals("abcd", writer.output.toByteArray().decodeToString())
        assertEquals(listOf("abcd", "cd", "cd"), writer.requests)
    }

    @Test
    fun errorBeforeProgressPreservesTheEntireBuffer() {
        val failure = IoError(IoErrorKind.TimedOut, "timeout")
        val buffered = seeded(Writer(listOf(error(failure))))
        val result = assertIs<Poll.Ready<Try<Unit, IoError>>>(buffered.pollFlush(TaskContext())).value
        assertSame(failure, assertIs<Try.Err<IoError>>(result).error)
        assertEquals("abcd", buffered.buffer().decodeToString())
        assertEquals(0, buffered.spareCapacity())
    }
}
