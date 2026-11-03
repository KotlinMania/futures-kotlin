// port-lint: tests futures/tests/io_read_line.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.Cursor
import io.github.kotlinmania.futures.io.IoErrorKind
import io.github.kotlinmania.futures.io.readLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadLineTest {
    private val context = TaskContext()

    @Test
    fun testReadLine() {
        val buf = Cursor("12".encodeToByteArray())
        val v = StringBuilder()
        val res1 = buf.readLine(v).poll(context)
        assertTrue(res1 is Poll.Ready)
        val val1 = res1.value
        assertTrue(val1 is Try.Ok)
        assertEquals(2, val1.value)
        assertEquals("12", v.toString())

        val buf2 = Cursor("12\n\n".encodeToByteArray())
        val v2 = StringBuilder()
        val res2 = buf2.readLine(v2).poll(context)
        assertTrue(res2 is Poll.Ready)
        val val2 = res2.value
        assertTrue(val2 is Try.Ok)
        assertEquals(3, val2.value)
        assertEquals("12\n", v2.toString())

        v2.clear()
        val res3 = buf2.readLine(v2).poll(context)
        assertTrue(res3 is Poll.Ready)
        val val3 = res3.value
        assertTrue(val3 is Try.Ok)
        assertEquals(1, val3.value)
        assertEquals("\n", v2.toString())

        v2.clear()
        val res4 = buf2.readLine(v2).poll(context)
        assertTrue(res4 is Poll.Ready)
        val val4 = res4.value
        assertTrue(val4 is Try.Ok)
        assertEquals(0, val4.value)
        assertEquals("", v2.toString())
    }

    @Test
    fun testReadLineUtf8Error() {
        val bytes = byteArrayOf('1'.code.toByte(), '2'.code.toByte(), 0xFF.toByte(), '\n'.code.toByte(), '\n'.code.toByte())
        val buf = Cursor(bytes)
        val v = StringBuilder("abc")
        val res = buf.readLine(v).poll(context)
        assertTrue(res is Poll.Ready)
        val valRes = res.value
        assertTrue(valRes is Try.Err)
        assertEquals(IoErrorKind.InvalidData, valRes.error.kind)
    }

    @Test
    fun readLine() {
        val buf = Cursor("12".encodeToByteArray())
        val v = StringBuilder()
        val res1 = buf.readLine(v).poll(context)
        assertTrue(res1 is Poll.Ready)
        val val1 = res1.value
        assertTrue(val1 is Try.Ok)
        assertEquals(2, val1.value)
        assertEquals("12", v.toString())

        val buf2 = Cursor("12\n\n".encodeToByteArray())
        val v2 = StringBuilder()
        val res2 = buf2.readLine(v2).poll(context)
        assertTrue(res2 is Poll.Ready)
        val val2 = res2.value
        assertTrue(val2 is Try.Ok)
        assertEquals(3, val2.value)
        assertEquals("12\n", v2.toString())

        v2.clear()
        val res3 = buf2.readLine(v2).poll(context)
        assertTrue(res3 is Poll.Ready)
        val val3 = res3.value
        assertTrue(val3 is Try.Ok)
        assertEquals(1, val3.value)
        assertEquals("\n", v2.toString())

        v2.clear()
        val res4 = buf2.readLine(v2).poll(context)
        assertTrue(res4 is Poll.Ready)
        val val4 = res4.value
        assertTrue(val4 is Try.Ok)
        assertEquals(0, val4.value)
        assertEquals("", v2.toString())
    }

    @Test
    fun readLineDrop() {
        val buf = Cursor("12\n\n".encodeToByteArray())
        val v = StringBuilder("abc")
        val fut = buf.readLine(v)
        assertEquals("abc", v.toString())
    }

    @Test
    fun readLineUtf8Error() {
        val bytes = byteArrayOf('1'.code.toByte(), '2'.code.toByte(), 0xFF.toByte(), '\n'.code.toByte(), '\n'.code.toByte())
        val buf = Cursor(bytes)
        val v = StringBuilder("abc")
        val res = buf.readLine(v).poll(context)
        assertTrue(res is Poll.Ready)
        val valRes = res.value
        assertTrue(valRes is Try.Err)
        assertEquals(IoErrorKind.InvalidData, valRes.error.kind)
        assertEquals("abc", v.toString())
    }
}
