// port-lint: tests futures/tests/io_cursor.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.Cursor
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IoCursorTest {
    private val context = TaskContext()

    @Test
    fun testCursorAsyncWrite() {
        val cursor = Cursor.new(ByteArray(5))
        val res1 = cursor.pollWrite(context, byteArrayOf(1, 2), 0, 2)
        assertTrue(res1 is Poll.Ready)
        assertEquals(Try.ok(2), res1.value)

        val res2 = cursor.pollWrite(context, byteArrayOf(3, 4), 0, 2)
        assertTrue(res2 is Poll.Ready)
        assertEquals(Try.ok(2), res2.value)

        val res3 = cursor.pollWrite(context, byteArrayOf(5, 6), 0, 2)
        assertTrue(res3 is Poll.Ready)
        assertEquals(Try.ok(2), res3.value)

        val res4 = cursor.pollWrite(context, byteArrayOf(6, 7), 0, 2)
        assertTrue(res4 is Poll.Ready)
        assertEquals(Try.ok(2), res4.value)

        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 6, 7), cursor.intoInner())
    }
}
