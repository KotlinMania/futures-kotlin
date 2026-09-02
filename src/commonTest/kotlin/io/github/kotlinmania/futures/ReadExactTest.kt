// port-lint: tests futures/tests/io_read_exact.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.Cursor
import io.github.kotlinmania.futures.io.IoErrorKind
import io.github.kotlinmania.futures.io.readExact
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadExactTest {
    private val context = TaskContext()

    @Test
    fun testReadExact() {
        val reader = Cursor(byteArrayOf(1, 2, 3, 4, 5))
        val out = ByteArray(3)

        val res1 = reader.readExact(out).poll(context)
        assertTrue(res1 is Poll.Ready)
        assertTrue(res1.value is Try.Ok)
        assertContentEquals(byteArrayOf(1, 2, 3), out)
        assertEquals(3L, reader.position())

        val res2 = reader.readExact(out).poll(context)
        assertTrue(res2 is Poll.Ready)
        val val2 = res2.value
        assertTrue(val2 is Try.Err)
        assertEquals(IoErrorKind.UnexpectedEof, val2.error.kind)
        assertEquals(5L, reader.position())
    }
}
