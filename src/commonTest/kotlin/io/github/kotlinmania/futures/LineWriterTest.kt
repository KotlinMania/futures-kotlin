// port-lint: tests futures-util/tests/io_line_writer.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.AsyncWrite
import io.github.kotlinmania.futures.io.IoError
import io.github.kotlinmania.futures.io.LineWriter
import io.github.kotlinmania.futures.io.lineWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LineWriterTest {
    private val context = TaskContext()

    private class MockWriter : AsyncWrite {
        val written = mutableListOf<Byte>()
        var flushCount = 0
        var closeCount = 0

        override fun pollWrite(
            context: TaskContext,
            buf: ByteArray,
            offset: Int,
            length: Int,
        ): Poll<Try<Int, IoError>> {
            for (i in offset until offset + length) {
                written.add(buf[i])
            }
            return Poll.Ready(Try.ok(length))
        }

        override fun pollFlush(context: TaskContext): Poll<Try<Unit, IoError>> {
            flushCount++
            return Poll.Ready(Try.ok(Unit))
        }

        override fun pollClose(context: TaskContext): Poll<Try<Unit, IoError>> {
            closeCount++
            return Poll.Ready(Try.ok(Unit))
        }
    }

    @Test
    fun testLineWriterBuffersUntilNewline() {
        val mock = MockWriter()
        val writer = mock.lineWriter(capacity = 64)

        val hello = "hello ".encodeToByteArray()
        val poll1 = writer.pollWrite(context, hello, 0, hello.size)
        assertTrue(poll1 is Poll.Ready)
        assertEquals(hello.size, (poll1.value as Try.Ok).value)
        assertEquals(0, mock.written.size) // still buffered

        val world = "world\n".encodeToByteArray()
        val poll2 = writer.pollWrite(context, world, 0, world.size)
        assertTrue(poll2 is Poll.Ready)
        assertEquals(world.size, (poll2.value as Try.Ok).value)

        // Buffered "hello " and "world\n" should now be written to mock
        assertEquals("hello world\n", mock.written.toByteArray().decodeToString())
    }

    @Test
    fun testLineWriterFlushAndClose() {
        val mock = MockWriter()
        val writer = LineWriter(mock, capacity = 32)

        val partial = "no newline".encodeToByteArray()
        writer.pollWrite(context, partial, 0, partial.size)
        assertEquals(0, mock.written.size)

        val flushRes = writer.pollFlush(context)
        assertTrue(flushRes is Poll.Ready)
        assertEquals("no newline", mock.written.toByteArray().decodeToString())

        writer.pollClose(context)
        assertEquals(1, mock.closeCount)
    }
}
