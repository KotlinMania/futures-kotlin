// port-lint: tests futures-util/tests/io_copy_buf_abortable.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.Cursor
import io.github.kotlinmania.futures.io.copyBufAbortable
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CopyBufAbortableTest {
    private val context = TaskContext()

    @Test
    fun testCopyBufAbortableCompletesSuccessfully() {
        val srcData = byteArrayOf(1, 2, 3, 4, 5)
        val reader = Cursor(srcData)
        val writer = Cursor(ByteArray(10))

        val (fut, handle) = copyBufAbortable(reader, writer)
        assertEquals(false, handle.isAborted())

        val pollRes = fut.poll(context)
        assertTrue(pollRes is Poll.Ready)
        val value = pollRes.value
        assertTrue(value is Try.Ok)
        val innerVal = value.value
        assertTrue(innerVal is Try.Ok)
        assertEquals(5L, innerVal.value)
        assertContentEquals(srcData, writer.getRef().copyOfRange(0, 5))
    }

    @Test
    fun testCopyBufAbortableAborts() {
        val srcData = byteArrayOf(1, 2, 3, 4, 5)
        val reader = Cursor(srcData)
        val writer = Cursor(ByteArray(10))

        val (fut, handle) = copyBufAbortable(reader, writer)
        handle.abort()
        assertEquals(true, handle.isAborted())

        val pollRes = fut.poll(context)
        assertTrue(pollRes is Poll.Ready)
        val value = pollRes.value
        assertTrue(value is Try.Ok)
        val innerVal = value.value
        assertTrue(innerVal is Try.Err)
        assertEquals(Aborted, innerVal.error)
    }
}
