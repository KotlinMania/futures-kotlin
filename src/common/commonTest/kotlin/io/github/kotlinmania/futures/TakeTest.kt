// port-lint: tests futures-util/tests/io_take.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.Cursor
import io.github.kotlinmania.futures.io.take
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TakeTest {
    private val context = TaskContext()

    @Test
    fun testTakeLimit() {
        val cursor = Cursor("12345678".encodeToByteArray())
        val takeReader = cursor.take(4L)
        val buf = ByteArray(2)

        val poll1 = takeReader.pollRead(context, buf)
        assertTrue(poll1 is Poll.Ready)
        val val1 = poll1.value
        assertTrue(val1 is Try.Ok)
        assertEquals(2, val1.value)
        assertEquals(2L, takeReader.limit())
        assertContentEquals("12".encodeToByteArray(), buf)

        val buf2 = ByteArray(4)
        val poll2 = takeReader.pollRead(context, buf2)
        assertTrue(poll2 is Poll.Ready)
        val val2 = poll2.value
        assertTrue(val2 is Try.Ok)
        assertEquals(2, val2.value)
        assertEquals(0L, takeReader.limit())
        assertContentEquals("34".encodeToByteArray(), buf2.copyOf(2))

        val poll3 = takeReader.pollRead(context, buf2)
        assertTrue(poll3 is Poll.Ready)
        val val3 = poll3.value
        assertTrue(val3 is Try.Ok)
        assertEquals(0, val3.value)
    }

    @Test
    fun testTakeSetLimit() {
        val cursor = Cursor("12345678".encodeToByteArray())
        val takeReader = cursor.take(4L)
        val buf = ByteArray(4)

        val poll1 = takeReader.pollRead(context, buf)
        assertTrue(poll1 is Poll.Ready)
        val val1 = poll1.value
        assertTrue(val1 is Try.Ok)
        assertEquals(4, val1.value)
        assertEquals(0L, takeReader.limit())

        takeReader.setLimit(10L)
        val poll2 = takeReader.pollRead(context, buf)
        assertTrue(poll2 is Poll.Ready)
        val val2 = poll2.value
        assertTrue(val2 is Try.Ok)
        assertEquals(4, val2.value)
        assertEquals(6L, takeReader.limit())
    }
}
