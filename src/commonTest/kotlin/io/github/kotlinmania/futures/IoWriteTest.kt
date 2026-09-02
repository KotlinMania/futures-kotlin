// port-lint: tests futures/tests/io_write.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.AsyncWrite
import io.github.kotlinmania.futures.io.IoError
import io.github.kotlinmania.futures.io.IoErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IoWriteTest {
    private val context = TaskContext()

    private class MockWriter(
        private val callback: (ByteArray, Int, Int) -> Poll<Try<Int, IoError>>,
    ) : AsyncWrite {
        override fun pollWrite(
            context: TaskContext,
            buf: ByteArray,
            offset: Int,
            length: Int,
        ): Poll<Try<Int, IoError>> = callback(buf, offset, length)

        override fun pollFlush(context: TaskContext): Poll<Try<Unit, IoError>> =
            Poll.Ready(Try.ok(Unit))

        override fun pollClose(context: TaskContext): Poll<Try<Unit, IoError>> =
            Poll.Ready(Try.ok(Unit))
    }

    @Test
    fun testMockWriterEmpty() {
        val writer = MockWriter { buf, offset, length ->
            assertEquals(0, length)
            Poll.Ready(Try.err(IoError.from(IoErrorKind.BrokenPipe)))
        }

        val res = writer.pollWrite(context, ByteArray(0), 0, 0)
        assertTrue(res is Poll.Ready)
        val valRes = res.value
        assertTrue(valRes is Try.Err)
        assertEquals(IoErrorKind.BrokenPipe, valRes.error.kind)
    }

    @Test
    fun testMockWriterWrite() {
        val writer = MockWriter { buf, offset, length ->
            assertEquals(4, length)
            Poll.Ready(Try.ok(4))
        }

        val res = writer.pollWrite(context, "four".encodeToByteArray(), 0, 4)
        assertTrue(res is Poll.Ready)
        assertEquals(Try.ok(4), res.value)
    }
}
