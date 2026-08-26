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
}
