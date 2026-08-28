// port-lint: tests futures-util/tests/io_seek.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.Cursor
import io.github.kotlinmania.futures.io.SeekFrom
import io.github.kotlinmania.futures.io.seek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeekTest {
    private val context = TaskContext()

    @Test
    fun testSeekFuture() {
        val cursor = Cursor(ByteArray(100))
        val seekFut = cursor.seek(SeekFrom.Start(42L))

        val pollRes = seekFut.poll(context)
        assertTrue(pollRes is Poll.Ready)
        val value = pollRes.value
        assertTrue(value is Try.Ok)
        assertEquals(42L, value.value)
        assertEquals(42L, cursor.position())
    }
}
