// port-lint: tests futures/tests/io_read_to_string.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.Cursor
import io.github.kotlinmania.futures.io.IoErrorKind
import io.github.kotlinmania.futures.io.readToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadToStringTest {
    private val context = TaskContext()

    @Test
    fun testReadToStringEmpty() {
        val cursor = Cursor(ByteArray(0))
        val sb = StringBuilder()
        val pollRes = cursor.readToString(sb).poll(context)
        assertTrue(pollRes is Poll.Ready)
        val value = pollRes.value
        assertTrue(value is Try.Ok)
        assertEquals(0, value.value)
        assertEquals("", sb.toString())
    }

    @Test
    fun testReadToStringNonEmpty() {
        val cursor = Cursor("1".encodeToByteArray())
        val sb = StringBuilder()
        val pollRes = cursor.readToString(sb).poll(context)
        assertTrue(pollRes is Poll.Ready)
        val value = pollRes.value
        assertTrue(value is Try.Ok)
        assertEquals(1, value.value)
        assertEquals("1", sb.toString())
    }

    @Test
    fun testReadToStringInvalidUtf8() {
        val cursor = Cursor(byteArrayOf(0xFF.toByte()))
        val sb = StringBuilder()
        val pollRes = cursor.readToString(sb).poll(context)
        assertTrue(pollRes is Poll.Ready)
        val value = pollRes.value
        assertTrue(value is Try.Err)
        assertEquals(IoErrorKind.InvalidData, value.error.kind)
    }
}
