// port-lint: tests futures-util/tests/io_seek.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.Cursor
import io.github.kotlinmania.futures.io.IoErrorKind
import io.github.kotlinmania.futures.io.SeekFrom
import io.github.kotlinmania.futures.io.seek
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeekTest {
    private val context = TaskContext()

    @Test
    fun testSeekFuture() {
        val cursor = Cursor(ByteArray(100))
        val seekFut = cursor.seek(SeekFrom.Start(42uL))

        val pollRes = seekFut.poll(context)
        assertTrue(pollRes is Poll.Ready)
        val value = pollRes.value
        assertTrue(value is Try.Ok)
        assertEquals(42uL, value.value)
        assertEquals(42uL, cursor.position())
    }

    @Test
    fun testSeekFromStartFullUnsigned64Width() {
        val positions =
            listOf(
                0uL,
                Int.MAX_VALUE.toULong() + 1uL,
                UInt.MAX_VALUE.toULong() + 1uL,
                Long.MAX_VALUE.toULong() + 1uL,
                ULong.MAX_VALUE,
            )
        for (target in positions) {
            val cursor = Cursor(byteArrayOf(10, 20, 30))
            val start = SeekFrom.Start(target)
            assertEquals(target, start.offset)

            val pollRes = cursor.pollSeek(context, start)
            assertTrue(pollRes is Poll.Ready)
            val seekVal = pollRes.value
            assertTrue(seekVal is Try.Ok)
            assertEquals(target, seekVal.value)
            assertEquals(target, cursor.position())

            if (target > 3uL) {
                val readBuf = byteArrayOf(99, 99)
                val readRes = cursor.pollRead(context, readBuf, 0, 2)
                assertTrue(readRes is Poll.Ready)
                assertEquals(0, (readRes.value as Try.Ok).value)
                assertContentEquals(byteArrayOf(99, 99), readBuf)
                assertEquals(target, cursor.position())

                val fillRes = cursor.pollFillBuf(context)
                assertTrue(fillRes is Poll.Ready)
                assertEquals(0, (fillRes.value as Try.Ok).value.size)
                cursor.consume(0)
                assertEquals(target, cursor.position())
            }
        }
    }

    @Test
    fun testCheckedRelativeSeekPreservesPositionOnError() {
        val cursor = Cursor(byteArrayOf(1, 2, 3, 4, 5))
        cursor.setPosition(2uL)

        // Underflow below 0 via Current(-3) preserves old position 2uL
        val negRes = cursor.pollSeek(context, SeekFrom.Current(-3L))
        assertTrue(negRes is Poll.Ready)
        val negErr = negRes.value
        assertTrue(negErr is Try.Err)
        assertEquals(IoErrorKind.InvalidInput, negErr.error.kind)
        assertEquals(2uL, cursor.position())

        // Underflow below 0 via End(-6) on 5-byte buffer preserves old position 2uL
        val endNegRes = cursor.pollSeek(context, SeekFrom.End(-6L))
        assertTrue(endNegRes is Poll.Ready)
        val endNegErr = endNegRes.value
        assertTrue(endNegErr is Try.Err)
        assertEquals(IoErrorKind.InvalidInput, endNegErr.error.kind)
        assertEquals(2uL, cursor.position())

        // Long.MIN_VALUE from Long.MAX_VALUE.toULong() underflows by 1 and preserves old position
        val highBelowHalf = Long.MAX_VALUE.toULong()
        cursor.setPosition(highBelowHalf)
        val minUnderflowRes = cursor.pollSeek(context, SeekFrom.Current(Long.MIN_VALUE))
        assertTrue(minUnderflowRes is Poll.Ready)
        assertTrue(minUnderflowRes.value is Try.Err)
        assertEquals(highBelowHalf, cursor.position())

        // Long.MIN_VALUE from Long.MAX_VALUE.toULong() + 1uL lands on 0uL
        val exactHalf = Long.MAX_VALUE.toULong() + 1uL
        cursor.setPosition(exactHalf)
        val minExactRes = cursor.pollSeek(context, SeekFrom.Current(Long.MIN_VALUE))
        assertTrue(minExactRes is Poll.Ready)
        assertEquals(0uL, (minExactRes.value as Try.Ok).value)
        assertEquals(0uL, cursor.position())

        // Overflow above ULong.MAX_VALUE preserves ULong.MAX_VALUE
        cursor.setPosition(ULong.MAX_VALUE)
        val overflowRes = cursor.pollSeek(context, SeekFrom.Current(1L))
        assertTrue(overflowRes is Poll.Ready)
        val overflowErr = overflowRes.value
        assertTrue(overflowErr is Try.Err)
        assertEquals(IoErrorKind.InvalidInput, overflowErr.error.kind)
        assertEquals(ULong.MAX_VALUE, cursor.position())
    }
}
