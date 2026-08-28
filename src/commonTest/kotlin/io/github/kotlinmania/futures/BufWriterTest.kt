// port-lint: tests futures-util/tests/io_buf_writer.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.AsyncWrite
import io.github.kotlinmania.futures.io.BufWriter
import io.github.kotlinmania.futures.io.Cursor
import io.github.kotlinmania.futures.io.IoError
import io.github.kotlinmania.futures.io.IoErrorKind
import io.github.kotlinmania.futures.io.SeekFrom
import io.github.kotlinmania.futures.io.bufWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BufWriterTest {
    private val context = TaskContext()

    private class MockWriter : AsyncWrite {
        val written = mutableListOf<Byte>()
        var flushCount = 0
        var closeCount = 0
        var failNextWrite = false

        override fun pollWrite(
            context: TaskContext,
            buf: ByteArray,
            offset: Int,
            length: Int,
        ): Poll<Try<Int, IoError>> {
            if (failNextWrite) {
                failNextWrite = false
                return Poll.Ready(Try.err(IoError(IoErrorKind.Other, "mock write failed")))
            }
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
    fun testBufWriterBuffering() {
        val mock = MockWriter()
        val writer = BufWriter(mock, capacity = 10)

        assertEquals(10, writer.capacity())
        assertEquals(10, writer.spareCapacity())
        assertEquals(0, writer.buffer().size)

        val writeRes1 = writer.pollWrite(context, byteArrayOf(1, 2, 3), 0, 3)
        assertTrue(writeRes1 is Poll.Ready)
        assertEquals(3, (writeRes1.value as Try.Ok).value)

        // Mock has not received writes yet because it is buffered
        assertEquals(0, mock.written.size)
        assertEquals(7, writer.spareCapacity())
        assertContentEquals(byteArrayOf(1, 2, 3), writer.buffer())

        // Flush pushes buffer to inner writer
        val flushRes = writer.pollFlush(context)
        assertTrue(flushRes is Poll.Ready)
        assertTrue(flushRes.value is Try.Ok)
        assertEquals(3, mock.written.size)
        assertContentEquals(listOf<Byte>(1, 2, 3), mock.written)
        assertEquals(10, writer.spareCapacity())
    }

    @Test
    fun testBufWriterOverflowFlushes() {
        val mock = MockWriter()
        val writer = mock.bufWriter(capacity = 5)

        writer.pollWrite(context, byteArrayOf(1, 2, 3), 0, 3)
        assertEquals(0, mock.written.size)

        // Writing 4 bytes exceeds remaining capacity (2), so it flushes then buffers
        val writeRes2 = writer.pollWrite(context, byteArrayOf(4, 5, 6, 7), 0, 4)
        assertTrue(writeRes2 is Poll.Ready)
        assertEquals(4, (writeRes2.value as Try.Ok).value)
        assertContentEquals(listOf<Byte>(1, 2, 3), mock.written)

        writer.pollClose(context)
        assertContentEquals(listOf<Byte>(1, 2, 3, 4, 5, 6, 7), mock.written)
        assertEquals(1, mock.closeCount)
    }

    @Test
    fun testBufWriterBypassLargeWrite() {
        val mock = MockWriter()
        val writer = mock.bufWriter(capacity = 4)

        // A write >= capacity bypasses buffer
        val writeRes = writer.pollWrite(context, byteArrayOf(10, 20, 30, 40, 50), 0, 5)
        assertTrue(writeRes is Poll.Ready)
        assertEquals(5, (writeRes.value as Try.Ok).value)
        assertContentEquals(listOf<Byte>(10, 20, 30, 40, 50), mock.written)
        assertEquals(0, writer.buffer().size)
    }

    @Test
    fun testBufWriterSeekFlushesBuffer() {
        val cursor = Cursor(ByteArray(20))
        val writer = BufWriter(cursor, capacity = 10)

        writer.pollWrite(context, byteArrayOf(1, 2, 3), 0, 3)
        assertEquals(0L, cursor.position())

        val seekRes = writer.pollSeek(context, SeekFrom.Start(5L))
        assertTrue(seekRes is Poll.Ready)
        assertEquals(5L, (seekRes.value as Try.Ok).value)
        assertEquals(5L, cursor.position())
        assertContentEquals(byteArrayOf(1, 2, 3), cursor.getRef().copyOfRange(0, 3))
    }
}
