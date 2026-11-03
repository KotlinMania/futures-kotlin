// port-lint: tests futures/tests/io_read_until.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.Cursor
import io.github.kotlinmania.futures.io.readUntil
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadUntilTest {
    private val context = TaskContext()

    @Test
    fun testReadUntil() {
        val buf = Cursor("12".encodeToByteArray())
        val v = mutableListOf<Byte>()
        val pollRes = buf.readUntil('3'.code.toByte(), v).poll(context)
        assertTrue(pollRes is Poll.Ready)
        val val1 = pollRes.value
        assertTrue(val1 is Try.Ok)
        assertEquals(2, val1.value)
        assertContentEquals("12".encodeToByteArray(), v.toByteArray())

        val buf2 = Cursor("1233".encodeToByteArray())
        val v2 = mutableListOf<Byte>()
        val pollRes2 = buf2.readUntil('3'.code.toByte(), v2).poll(context)
        assertTrue(pollRes2 is Poll.Ready)
        val val2 = pollRes2.value
        assertTrue(val2 is Try.Ok)
        assertEquals(3, val2.value)
        assertContentEquals("123".encodeToByteArray(), v2.toByteArray())

        v2.clear()
        val pollRes3 = buf2.readUntil('3'.code.toByte(), v2).poll(context)
        assertTrue(pollRes3 is Poll.Ready)
        val val3 = pollRes3.value
        assertTrue(val3 is Try.Ok)
        assertEquals(1, val3.value)
        assertContentEquals("3".encodeToByteArray(), v2.toByteArray())

        v2.clear()
        val pollRes4 = buf2.readUntil('3'.code.toByte(), v2).poll(context)
        assertTrue(pollRes4 is Poll.Ready)
        val val4 = pollRes4.value
        assertTrue(val4 is Try.Ok)
        assertEquals(0, val4.value)
        assertEquals(0, v2.size)
    }
}
