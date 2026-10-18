package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VectoredIoTest {
    @Test
    fun testReadVectored() {
        val data = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val cursor = Cursor(data)
        val buf1 = ByteArray(3)
        val buf2 = ByteArray(5)
        val slices = listOf(IoSliceMut(buf1), IoSliceMut(buf2))

        val cx = TaskContext()
        val readFuture = cursor.readVectored(slices)
        val poll = readFuture.poll(cx)

        assertIs<Poll.Ready<Try<Int, IoError>>>(poll)
        val res = poll.value
        assertIs<Try.Ok<Int>>(res)
        assertEquals(3, res.value)
        assertContentEquals(byteArrayOf(1, 2, 3), buf1)
    }

    @Test
    fun testWriteVectored() {
        val cursor = Cursor()
        val slice1 = IoSlice(byteArrayOf(10, 20))
        val slice2 = IoSlice(byteArrayOf(30, 40))
        val slices = listOf(slice1, slice2)

        val cx = TaskContext()
        val writeFuture = cursor.writeVectored(slices)
        val poll = writeFuture.poll(cx)

        assertIs<Poll.Ready<Try<Int, IoError>>>(poll)
        val res = poll.value
        assertIs<Try.Ok<Int>>(res)
        assertEquals(2, res.value)
        assertContentEquals(byteArrayOf(10, 20), cursor.getRef())
    }
}
