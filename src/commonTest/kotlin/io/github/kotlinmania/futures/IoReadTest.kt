// port-lint: tests futures/tests/io_read.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.AsyncRead
import io.github.kotlinmania.futures.io.IoError
import io.github.kotlinmania.futures.io.IoErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IoReadTest {
    private val context = TaskContext()

    private class MockReader(
        private val callback: (ByteArray, Int, Int) -> Poll<Try<Int, IoError>>,
    ) : AsyncRead {
        override fun pollRead(
            context: TaskContext,
            buf: ByteArray,
            offset: Int,
            length: Int,
        ): Poll<Try<Int, IoError>> = callback(buf, offset, length)
    }

    @Test
    fun testMockReaderEmpty() {
        val reader =
            MockReader { buf, offset, length ->
                assertEquals(0, length)
                Poll.Ready(Try.err(IoError.from(IoErrorKind.BrokenPipe)))
            }

        val res = reader.pollRead(context, ByteArray(0), 0, 0)
        assertTrue(res is Poll.Ready)
        val valRes = res.value
        assertTrue(valRes is Try.Err)
        assertEquals(IoErrorKind.BrokenPipe, valRes.error.kind)
    }

    @Test
    fun testMockReaderRead() {
        val reader =
            MockReader { buf, offset, length ->
                assertEquals(4, length)
                "four".encodeToByteArray().copyInto(buf, destinationOffset = offset)
                Poll.Ready(Try.ok(4))
            }

        val buf = ByteArray(4)
        val res = reader.pollRead(context, buf, 0, 4)
        assertTrue(res is Poll.Ready)
        assertEquals(Try.ok(4), res.value)
        assertEquals("four", buf.decodeToString())
    }
}
