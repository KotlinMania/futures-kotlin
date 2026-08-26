// port-lint: tests futures-util/tests/io_cursor.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.BufReader
import io.github.kotlinmania.futures.io.Cursor
import io.github.kotlinmania.futures.io.SeekFrom
import io.github.kotlinmania.futures.io.copy
import io.github.kotlinmania.futures.io.empty
import io.github.kotlinmania.futures.io.readExact
import io.github.kotlinmania.futures.io.readToEnd
import io.github.kotlinmania.futures.io.repeat
import io.github.kotlinmania.futures.io.writeAll
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AsyncIoTest {
    private val context = TaskContext()

    @Test
    fun testEmptyReader() {
        val reader = empty()
        val buf = ByteArray(10)
        val pollRes = reader.pollRead(context, buf)
        assertTrue(pollRes is Poll.Ready)
        val value = pollRes.value
        assertTrue(value is Try.Ok)
        assertEquals(0, value.value)
    }

    @Test
    fun testRepeatReader() {
        val reader = repeat(42.toByte())
        val buf = ByteArray(5)
        val pollRes = reader.pollRead(context, buf)
        assertTrue(pollRes is Poll.Ready)
        val value = pollRes.value
        assertTrue(value is Try.Ok)
        assertEquals(5, value.value)
        assertContentEquals(byteArrayOf(42, 42, 42, 42, 42), buf)
    }

    @Test
    fun testCursorReadAndSeek() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val cursor = Cursor(data)

        assertEquals(0L, cursor.position())
        val buf = ByteArray(3)
        val readPoll = cursor.pollRead(context, buf)
        assertTrue(readPoll is Poll.Ready)
        val readVal = readPoll.value
        assertTrue(readVal is Try.Ok)
        assertEquals(3, readVal.value)
        assertEquals(3L, cursor.position())
        assertContentEquals(byteArrayOf(1, 2, 3), buf)

        val seekPoll = cursor.pollSeek(context, SeekFrom.Start(1L))
        assertTrue(seekPoll is Poll.Ready)
        val seekVal = seekPoll.value
        assertTrue(seekVal is Try.Ok)
        assertEquals(1L, seekVal.value)
        assertEquals(1L, cursor.position())

        val readPoll2 = cursor.pollRead(context, buf, 0, 2)
        assertTrue(readPoll2 is Poll.Ready)
        val readVal2 = readPoll2.value
        assertTrue(readVal2 is Try.Ok)
        assertEquals(2, readVal2.value)
        assertEquals(2, buf[0])
        assertEquals(3, buf[1])
    }

    @Test
    fun testCursorWrite() {
        val cursor = Cursor()
        val data = byteArrayOf(10, 20, 30)
        val writePoll = cursor.pollWrite(context, data)
        assertTrue(writePoll is Poll.Ready)
        val writeVal = writePoll.value
        assertTrue(writeVal is Try.Ok)
        assertEquals(3, writeVal.value)
        assertEquals(3L, cursor.position())
        assertContentEquals(data, cursor.intoInner())
    }

    @Test
    fun testBufReader() {
        val cursor = Cursor(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val bufReader = BufReader(cursor, capacity = 4)

        val fillRes = bufReader.pollFillBuf(context)
        assertTrue(fillRes is Poll.Ready)
        val fillVal = fillRes.value
        assertTrue(fillVal is Try.Ok)
        val buf = fillVal.value
        assertEquals(4, buf.size)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), buf)

        bufReader.consume(2)
        val readBuf = ByteArray(4)
        val readRes = bufReader.pollRead(context, readBuf, 0, 2)
        assertTrue(readRes is Poll.Ready)
        val readVal = readRes.value
        assertTrue(readVal is Try.Ok)
        assertEquals(2, readVal.value)
        assertEquals(3, readBuf[0])
        assertEquals(4, readBuf[1])
    }

    @Test
    fun testCopy() {
        val src = Cursor(byteArrayOf(1, 2, 3, 4, 5))
        val dst = Cursor()

        val copyFuture = copy(src, dst)
        val pollRes = copyFuture.poll(context)
        assertTrue(pollRes is Poll.Ready)
        val copyVal = pollRes.value
        assertTrue(copyVal is Try.Ok)
        assertEquals(5L, copyVal.value)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), dst.intoInner())
    }

    @Test
    fun testReadExactAndWriteAll() {
        val src = Cursor(byteArrayOf(7, 8, 9))
        val dstBuf = ByteArray(3)
        val readExactFuture = src.readExact(dstBuf)
        val pollRes = readExactFuture.poll(context)
        assertTrue(pollRes is Poll.Ready)
        val readVal = pollRes.value
        assertTrue(readVal is Try.Ok)
        assertTrue(readVal.value == Unit)
        assertContentEquals(byteArrayOf(7, 8, 9), dstBuf)

        val dst = Cursor()
        val writeAllFuture = dst.writeAll(dstBuf)
        val writeRes = writeAllFuture.poll(context)
        assertTrue(writeRes is Poll.Ready)
        val writeVal = writeRes.value
        assertTrue(writeVal is Try.Ok)
        assertTrue(writeVal.value == Unit)
        assertContentEquals(byteArrayOf(7, 8, 9), dst.intoInner())
    }

    @Test
    fun testReadToEnd() {
        val src = Cursor(byteArrayOf(100, 101, 102))
        val list = mutableListOf<Byte>()
        val readToEndFuture = src.readToEnd(list)
        val pollRes = readToEndFuture.poll(context)
        assertTrue(pollRes is Poll.Ready)
        val readVal = pollRes.value
        assertTrue(readVal is Try.Ok)
        assertEquals(3, readVal.value)
        assertEquals(listOf<Byte>(100, 101, 102), list)
    }
}
