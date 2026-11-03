// port-lint: tests futures/tests/stream_into_async_read.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.IoError
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class StreamIntoAsyncReadTest {
    private fun assertRead(reader: IntoAsyncRead<*>, buf: ByteArray, expectedLen: Int) {
        val cx = noopContext()
        while (true) {
            when (val poll = reader.pollRead(cx, buf, 0, buf.size)) {
                is Poll.Ready -> {
                    when (val res = poll.value) {
                        is Try.Ok -> {
                            assertEquals(expectedLen, res.value)
                            break
                        }
                        is Try.Err -> error("assertion failed: expected value but got ${res.error}")
                    }
                }
                is Poll.Pending -> continue
            }
        }
    }

    private fun assertFillBuf(reader: IntoAsyncRead<*>, expected: ByteArray) {
        val cx = noopContext()
        while (true) {
            when (val poll = reader.pollFillBuf(cx)) {
                is Poll.Ready -> {
                    when (val res = poll.value) {
                        is Try.Ok -> {
                            assertContentEquals(expected, res.value)
                            break
                        }
                        is Try.Err -> error("assertion failed: expected value but got ${res.error}")
                    }
                }
                is Poll.Pending -> continue
            }
        }
    }

    @Test
    fun testIntoAsyncRead() {
        val chunks = mutableListOf<Try<ByteArray, IoError>>()
        for (i in 1..3) {
            chunks.add(Try.ok(ByteArray(0)))
            chunks.add(Try.ok(byteArrayOf(1, 2, 3, 4, 5)))
        }
        val stream = streamIter(chunks).asTryStream()
        val reader = stream.intoAsyncRead()
        val buf = ByteArray(3)

        assertRead(reader, buf, 3)
        assertContentEquals(byteArrayOf(1, 2, 3), buf)

        val buf2 = ByteArray(2)
        assertRead(reader, buf2, 2)
        assertContentEquals(byteArrayOf(4, 5), buf2)

        assertRead(reader, buf, 3)
        assertContentEquals(byteArrayOf(1, 2, 3), buf)

        assertRead(reader, buf2, 2)
        assertContentEquals(byteArrayOf(4, 5), buf2)

        assertRead(reader, buf, 3)
        assertContentEquals(byteArrayOf(1, 2, 3), buf)

        assertRead(reader, buf2, 2)
        assertContentEquals(byteArrayOf(4, 5), buf2)

        assertRead(reader, buf, 0)
    }

    @Test
    fun testIntoAsyncBufRead() {
        val chunks = mutableListOf<Try<ByteArray, IoError>>()
        for (i in 1..2) {
            chunks.add(Try.ok(ByteArray(0)))
            chunks.add(Try.ok(byteArrayOf(1, 2, 3, 4, 5)))
        }
        val stream = streamIter(chunks).asTryStream()
        val reader = stream.intoAsyncRead()

        assertFillBuf(reader, byteArrayOf(1, 2, 3, 4, 5))
        reader.consume(3)

        assertFillBuf(reader, byteArrayOf(4, 5))
        reader.consume(2)

        assertFillBuf(reader, byteArrayOf(1, 2, 3, 4, 5))
        reader.consume(2)

        assertFillBuf(reader, byteArrayOf(3, 4, 5))
        reader.consume(3)

        assertFillBuf(reader, ByteArray(0))
    }
}
