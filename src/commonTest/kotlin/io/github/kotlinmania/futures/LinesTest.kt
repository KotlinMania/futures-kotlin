// port-lint: tests futures/tests/io_lines.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.Cursor
import io.github.kotlinmania.futures.io.lines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinesTest {
    private val context = TaskContext()

    @Test
    fun testLinesCarriageReturn() {
        val buf = Cursor("12\r".encodeToByteArray())
        val linesStream = buf.lines()

        val item1 = linesStream.pollNext(context)
        assertTrue(item1 is Poll.Ready)
        val y1 = item1.value
        assertTrue(y1 is Yield.Value)
        val val1 = y1.value
        assertTrue(val1 is Try.Ok)
        assertEquals("12\r", val1.value)

        val item2 = linesStream.pollNext(context)
        assertTrue(item2 is Poll.Ready)
        assertTrue(item2.value is Yield.End)
    }

    @Test
    fun testLinesCrlfAndLf() {
        val buf = Cursor("12\r\n\n".encodeToByteArray())
        val linesStream = buf.lines()

        val item1 = linesStream.pollNext(context)
        assertTrue(item1 is Poll.Ready)
        val y1 = item1.value
        assertTrue(y1 is Yield.Value)
        val val1 = y1.value
        assertTrue(val1 is Try.Ok)
        assertEquals("12", val1.value)

        val item2 = linesStream.pollNext(context)
        assertTrue(item2 is Poll.Ready)
        val y2 = item2.value
        assertTrue(y2 is Yield.Value)
        val val2 = y2.value
        assertTrue(val2 is Try.Ok)
        assertEquals("", val2.value)

        val item3 = linesStream.pollNext(context)
        assertTrue(item3 is Poll.Ready)
        assertTrue(item3.value is Yield.End)
    }

    @Test
    fun lines() {
        val buf = Cursor("12\r".encodeToByteArray())
        val s = buf.lines()
        val item1 = s.pollNext(context)
        assertTrue(item1 is Poll.Ready)
        val y1 = item1.value
        assertTrue(y1 is Yield.Value)
        val val1 = y1.value
        assertTrue(val1 is Try.Ok)
        assertEquals("12\r", val1.value)

        val end1 = s.pollNext(context)
        assertTrue(end1 is Poll.Ready)
        assertTrue(end1.value is Yield.End)

        val buf2 = Cursor("12\r\n\n".encodeToByteArray())
        val s2 = buf2.lines()
        val i1 = s2.pollNext(context)
        assertTrue(i1 is Poll.Ready)
        assertEquals("12", ((i1.value as Yield.Value).value as Try.Ok).value)

        val i2 = s2.pollNext(context)
        assertTrue(i2 is Poll.Ready)
        assertEquals("", ((i2.value as Yield.Value).value as Try.Ok).value)

        val end2 = s2.pollNext(context)
        assertTrue(end2 is Poll.Ready)
        assertTrue(end2.value is Yield.End)
    }

    @Test
    fun maybePending() {
        val buf = Cursor("12\r".encodeToByteArray())
        val s = buf.lines()
        val item1 = s.pollNext(context)
        assertTrue(item1 is Poll.Ready)
        assertEquals("12\r", (((item1.value as Yield.Value).value) as Try.Ok).value)
        val end1 = s.pollNext(context)
        assertTrue((end1 as Poll.Ready).value is Yield.End)

        val buf2 = Cursor("12\r\n\n".encodeToByteArray())
        val s2 = buf2.lines()
        val i1 = s2.pollNext(context)
        assertEquals("12", (((i1 as Poll.Ready).value as Yield.Value).value as Try.Ok).value)
        val i2 = s2.pollNext(context)
        assertEquals("", (((i2 as Poll.Ready).value as Yield.Value).value as Try.Ok).value)
        val end2 = s2.pollNext(context)
        assertTrue((end2 as Poll.Ready).value is Yield.End)
    }

    @Test
    fun issue2862() {
        val failingReader =
            object : io.github.kotlinmania.futures.io.AsyncBufRead {
                var count = 0

                override fun pollFillBuf(context: TaskContext): Poll<Try<ByteArray, io.github.kotlinmania.futures.io.IoError>> {
                    return if (count == 0) {
                        count++
                        Poll.ready(Try.ok(byteArrayOf('x'.code.toByte())))
                    } else {
                        Poll.ready(Try.err(io.github.kotlinmania.futures.io.IoError(io.github.kotlinmania.futures.io.IoErrorKind.InvalidInput, "test error")))
                    }
                }

                override fun consume(amt: Int) {}

                override fun pollRead(
                    context: TaskContext,
                    buf: ByteArray,
                    offset: Int,
                    length: Int,
                ): Poll<Try<Int, io.github.kotlinmania.futures.io.IoError>> {
                    return Poll.ready(Try.err(io.github.kotlinmania.futures.io.IoError(io.github.kotlinmania.futures.io.IoErrorKind.InvalidInput, "test error")))
                }
            }
        val lines = failingReader.lines()
        val res = lines.pollNext(context)
        assertTrue(res is Poll.Ready)
    }
}
