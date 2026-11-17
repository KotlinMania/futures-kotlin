// port-lint: tests futures-util/src/io/write_all_vectored.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.AsyncWrite
import io.github.kotlinmania.futures.io.IoError
import io.github.kotlinmania.futures.io.IoSlice
import io.github.kotlinmania.futures.io.writeAllVectored
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WriteAllVectoredTest {
    private class TestWriter(
        private val nBufs: Int,
        private val perCall: Int,
    ) : AsyncWrite {
        val written: MutableList<Byte> = mutableListOf()

        override fun pollWrite(
            context: TaskContext,
            buf: ByteArray,
            offset: Int,
            length: Int,
        ): Poll<Try<Int, IoError>> =
            pollWriteVectored(context, listOf(IoSlice(buf, offset, length)))

        override fun pollWriteVectored(
            context: TaskContext,
            bufs: List<IoSlice>,
        ): Poll<Try<Int, IoError>> {
            var left = perCall
            var count = 0
            for (buf in bufs.take(nBufs)) {
                val n = minOf(left, buf.length)
                for (i in 0 until n) {
                    written.add(buf.buffer[buf.offset + i])
                }
                left -= n
                count += n
            }
            return Poll.Ready(Try.ok(count))
        }

        override fun pollFlush(context: TaskContext): Poll<Try<Unit, IoError>> =
            Poll.Ready(Try.ok(Unit))

        override fun pollClose(context: TaskContext): Poll<Try<Unit, IoError>> =
            Poll.Ready(Try.ok(Unit))
    }

    private fun testWriter(nBufs: Int, perCall: Int): TestWriter =
        TestWriter(nBufs, perCall)

    @Test
    fun testWriterReadFromOneBuf() {
        val cx = TaskContext()
        val dst = testWriter(1, 2)

        assertEquals(Poll.Ready(Try.ok(0)), dst.pollWrite(cx, ByteArray(0)))
        assertEquals(Poll.Ready(Try.ok(0)), dst.pollWriteVectored(cx, emptyList()))

        // Read at most 2 bytes
        assertEquals(Poll.Ready(Try.ok(2)), dst.pollWrite(cx, byteArrayOf(1, 1, 1)))
        val bufs = listOf(IoSlice(byteArrayOf(2, 2, 2)))
        assertEquals(Poll.Ready(Try.ok(2)), dst.pollWriteVectored(cx, bufs))

        // Only read from first buf
        val bufs2 = listOf(IoSlice(byteArrayOf(3)), IoSlice(byteArrayOf(4, 4)))
        assertEquals(Poll.Ready(Try.ok(1)), dst.pollWriteVectored(cx, bufs2))

        assertContentEquals(byteArrayOf(1, 1, 2, 2, 3), dst.written.toByteArray())
    }

    @Test
    fun testWriterReadFromMultipleBufs() {
        val cx = TaskContext()
        val dst = testWriter(3, 3)

        // Read at most 3 bytes from two buffers
        val bufs1 = listOf(IoSlice(byteArrayOf(1)), IoSlice(byteArrayOf(2, 2, 2)))
        assertEquals(Poll.Ready(Try.ok(3)), dst.pollWriteVectored(cx, bufs1))

        // Read at most 3 bytes from three buffers
        val bufs2 = listOf(IoSlice(byteArrayOf(3)), IoSlice(byteArrayOf(4)), IoSlice(byteArrayOf(5, 5)))
        assertEquals(Poll.Ready(Try.ok(3)), dst.pollWriteVectored(cx, bufs2))

        assertContentEquals(byteArrayOf(1, 2, 2, 3, 4, 5), dst.written.toByteArray())
    }

    @Test
    fun testWriteAllVectored() {
        val cx = TaskContext()

        val testCases: List<Pair<List<ByteArray>, ByteArray>> =
            listOf(
                emptyList<ByteArray>() to byteArrayOf(),
                listOf(byteArrayOf(), byteArrayOf()) to byteArrayOf(),
                listOf(byteArrayOf(1)) to byteArrayOf(1),
                listOf(byteArrayOf(1, 2)) to byteArrayOf(1, 2),
                listOf(byteArrayOf(1, 2, 3)) to byteArrayOf(1, 2, 3),
                listOf(byteArrayOf(1, 2, 3, 4)) to byteArrayOf(1, 2, 3, 4),
                listOf(byteArrayOf(1, 2, 3, 4, 5)) to byteArrayOf(1, 2, 3, 4, 5),
                listOf(byteArrayOf(1), byteArrayOf(2)) to byteArrayOf(1, 2),
                listOf(byteArrayOf(1, 1), byteArrayOf(2, 2)) to byteArrayOf(1, 1, 2, 2),
                listOf(byteArrayOf(1, 1, 1), byteArrayOf(2, 2, 2)) to byteArrayOf(1, 1, 1, 2, 2, 2),
                listOf(byteArrayOf(1, 1, 1, 1), byteArrayOf(2, 2, 2, 2)) to byteArrayOf(1, 1, 1, 1, 2, 2, 2, 2),
                listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3)) to byteArrayOf(1, 2, 3),
                listOf(byteArrayOf(1, 1), byteArrayOf(2, 2), byteArrayOf(3, 3)) to byteArrayOf(1, 1, 2, 2, 3, 3),
                listOf(byteArrayOf(1, 1, 1), byteArrayOf(2, 2, 2), byteArrayOf(3, 3, 3)) to byteArrayOf(1, 1, 1, 2, 2, 2, 3, 3, 3),
            )

        for ((inputArrays, wanted) in testCases) {
            val dst = testWriter(2, 2)
            val input = inputArrays.map { IoSlice(it) }.toMutableList()
            val fut = dst.writeAllVectored(input)
            val res = fut.poll(cx)
            assertTrue(res is Poll.Ready)
            assertEquals(Try.ok(Unit), res.value)
            assertContentEquals(wanted, dst.written.toByteArray())
        }
    }
}
