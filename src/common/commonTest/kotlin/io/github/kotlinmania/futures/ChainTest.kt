// port-lint: tests futures-util/tests/io_chain.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.Cursor
import io.github.kotlinmania.futures.io.chain
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChainTest {
    private val context = TaskContext()

    @Test
    fun testChain() {
        val first = Cursor("Hello, ".encodeToByteArray())
        val second = Cursor("World!".encodeToByteArray())
        val chained = first.chain(second)

        val buf = ByteArray(20)
        val poll1 = chained.pollRead(context, buf, 0, 7)
        assertTrue(poll1 is Poll.Ready)
        val val1 = poll1.value
        assertTrue(val1 is Try.Ok)
        assertEquals(7, val1.value)
        assertContentEquals("Hello, ".encodeToByteArray(), buf.copyOf(7))

        val poll2 = chained.pollRead(context, buf, 7, 13)
        assertTrue(poll2 is Poll.Ready)
        val val2 = poll2.value
        assertTrue(val2 is Try.Ok)
        assertEquals(6, val2.value)
        assertContentEquals("Hello, World!".encodeToByteArray(), buf.copyOf(13))

        val poll3 = chained.pollRead(context, buf, 13, 7)
        assertTrue(poll3 is Poll.Ready)
        val val3 = poll3.value
        assertTrue(val3 is Try.Ok)
        assertEquals(0, val3.value)
    }

    @Test
    fun testChainGettersAndInner() {
        val first = Cursor("abc".encodeToByteArray())
        val second = Cursor("def".encodeToByteArray())
        val chained = first.chain(second)

        val (ref1, ref2) = chained.getRef()
        assertEquals(first, ref1)
        assertEquals(second, ref2)

        val (mut1, mut2) = chained.getMut()
        assertEquals(first, mut1)
        assertEquals(second, mut2)

        val (inner1, inner2) = chained.intoInner()
        assertEquals(first, inner1)
        assertEquals(second, inner2)
    }

    @Test
    fun testChainBufRead() {
        val first = Cursor("Hello, ".encodeToByteArray())
        val second = Cursor("World!".encodeToByteArray())
        val chained = first.chain(second)

        val fill1 = chained.pollFillBuf(context)
        assertTrue(fill1 is Poll.Ready)
        val res1 = fill1.value
        assertTrue(res1 is Try.Ok)
        assertContentEquals("Hello, ".encodeToByteArray(), res1.value)

        chained.consume(7)

        val fill2 = chained.pollFillBuf(context)
        assertTrue(fill2 is Poll.Ready)
        val res2 = fill2.value
        assertTrue(res2 is Try.Ok)
        assertContentEquals("World!".encodeToByteArray(), res2.value)

        chained.consume(6)

        val fill3 = chained.pollFillBuf(context)
        assertTrue(fill3 is Poll.Ready)
        val res3 = fill3.value
        assertTrue(res3 is Try.Ok)
        assertEquals(0, res3.value.size)
    }
}
