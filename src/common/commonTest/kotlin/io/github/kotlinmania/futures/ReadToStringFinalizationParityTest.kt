package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.AsyncRead
import io.github.kotlinmania.futures.io.IoError
import io.github.kotlinmania.futures.io.IoErrorKind
import io.github.kotlinmania.futures.io.readToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class ReadToStringFinalizationParityTest {
    private class Reader(
        events: List<Poll<Try<ByteArray, IoError>>>,
    ) : AsyncRead {
        private val events = events.toMutableList()

        override fun pollRead(context: TaskContext, buf: ByteArray, offset: Int, length: Int): Poll<Try<Int, IoError>> =
            when (val event = events.removeAt(0)) {
                Poll.Pending -> {
                    context.wakeByRef()
                    Poll.Pending
                }

                is Poll.Ready -> {
                    when (val result = event.value) {
                        is Try.Ok -> {
                            check(result.value.size <= length)
                            result.value.copyInto(buf, offset)
                            Poll.Ready(Try.Ok(result.value.size))
                        }

                        is Try.Err -> {
                            Poll.Ready(result)
                        }
                    }
                }
            }
    }

    private fun bytes(vararg bytes: Byte): Poll<Try<ByteArray, IoError>> = Poll.Ready(Try.Ok(bytes))

    private fun error(error: IoError): Poll<Try<ByteArray, IoError>> = Poll.Ready(Try.Err(error))

    private fun result(poll: Poll<Try<Int, IoError>>): Try<Int, IoError> = assertIs<Poll.Ready<Try<Int, IoError>>>(poll).value

    @Test
    fun validPartialDataIsRestoredEvenWhenReadingFails() {
        val failure = IoError(IoErrorKind.Other, "reader failed")
        val output = StringBuilder("old:")
        val reader = Reader(listOf(bytes(0xC3.toByte(), 0xA9.toByte()), error(failure)))
        val read = result(reader.readToString(output).poll(TaskContext()))
        assertSame(failure, assertIs<Try.Err<IoError>>(read).error)
        assertEquals("old:é", output.toString())
    }

    @Test
    fun invalidUtf8AtEofLeavesTheMovedDestinationEmpty() {
        val output = StringBuilder("old:")
        val read = result(Reader(listOf(bytes(0xFF.toByte()), bytes())).readToString(output).poll(TaskContext()))
        val failure = assertIs<Try.Err<IoError>>(read).error
        assertEquals(IoErrorKind.InvalidData, failure.kind)
        assertEquals("stream did not contain valid UTF-8", failure.message)
        assertEquals("", output.toString())
    }

    @Test
    fun invalidUtf8DoesNotReplaceTheOriginalReadError() {
        val failure = IoError(IoErrorKind.TimedOut, "timed out")
        val output = StringBuilder("old:")
        val read = result(Reader(listOf(bytes(0xC3.toByte()), error(failure))).readToString(output).poll(TaskContext()))
        assertSame(failure, assertIs<Try.Err<IoError>>(read).error)
        assertEquals("", output.toString())
    }

    @Test
    fun splitUtf8AcrossPendingIsValidatedOnlyAtCompletion() {
        val output = StringBuilder("旧:")
        val future = Reader(listOf(bytes(0xF0.toByte(), 0x9F.toByte()), Poll.Pending, bytes(0x98.toByte(), 0x80.toByte()), bytes())).readToString(output)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(4, assertIs<Try.Ok<Int>>(result(future.poll(TaskContext()))).value)
        assertEquals("旧:😀", output.toString())
    }

    @Test
    fun abandoningAPendingReadDoesNotRestoreTheMovedOriginalBuffer() {
        val output = StringBuilder("old:")

        fun abandon() {
            val future = Reader(listOf(bytes(0x61), Poll.Pending)).readToString(output)
            assertSame(Poll.Pending, future.poll(TaskContext()))
        }
        abandon()
        assertEquals("", output.toString())
    }

    @Test
    fun successfulCountExcludesTheOriginalUtf8Prefix() {
        val output = StringBuilder("旧:")
        val read = result(Reader(listOf(bytes(0xC3.toByte(), 0xA9.toByte()), bytes())).readToString(output).poll(TaskContext()))
        assertEquals(2, assertIs<Try.Ok<Int>>(read).value)
        assertEquals("旧:é", output.toString())
    }

    @Test
    fun immediateEofRestoresTheOriginalBufferWithZeroNewBytes() {
        val output = StringBuilder("旧:")
        val read = result(Reader(listOf(bytes())).readToString(output).poll(TaskContext()))
        assertEquals(0, assertIs<Try.Ok<Int>>(read).value)
        assertEquals("旧:", output.toString())
    }
}
