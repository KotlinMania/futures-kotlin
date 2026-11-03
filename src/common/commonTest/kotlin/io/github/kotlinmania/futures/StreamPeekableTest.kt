// port-lint: tests futures/tests/stream_peekable.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals

class StreamPeekableTest {
    @Test
    fun testPeekable() {
        val cx = TaskContext()
        val peekable = streamIter(listOf(1, 2, 3)).peekable()

        val peekFut = peekable.peek()
        val peekRes = peekFut.poll(cx)
        assertEquals(Poll.ready(1), peekRes)

        val collectFut = peekable.collect()
        val collectRes = collectFut.poll(cx)
        assertEquals(Poll.ready(listOf(1, 2, 3)), collectRes)

        val s = streamOnce(ready(1)).peekable()
        val peekOnce = s.peek().poll(cx)
        assertEquals(Poll.ready(1), peekOnce)

        val collectOnce = s.collect().poll(cx)
        assertEquals(Poll.ready(listOf(1)), collectOnce)
    }

    @Test
    fun testPeekableNextIfEq() {
        val cx = TaskContext()
        val s = streamIter(listOf("Heart", "of", "Gold")).peekable()

        // try before peek()
        val p1 = s.nextIfEq("trillian").poll(cx)
        assertEquals(Poll.ready(null), p1)

        val p2 = s.nextIfEq("Heart").poll(cx)
        assertEquals(Poll.ready("Heart"), p2)

        // try after peek()
        val peekOf = s.peek().poll(cx)
        assertEquals(Poll.ready("of"), peekOf)

        val p3 = s.nextIfEq("of").poll(cx)
        assertEquals(Poll.ready("of"), p3)

        val p4 = s.nextIfEq("zaphod").poll(cx)
        assertEquals(Poll.ready(null), p4)

        // make sure next() still behaves
        val nextGold = s.next().poll(cx)
        assertEquals(Poll.ready("Gold"), nextGold)

        val endNext = s.next().poll(cx)
        assertEquals(Poll.ready(null), endNext)

        // test with String values
        val s2 = streamIter(listOf("Ludicrous", "speed")).peekable()
        val r1 = s2.nextIfEq("Ludicrous").poll(cx)
        assertEquals(Poll.ready("Ludicrous"), r1)

        val r2 = s2.nextIfEq("speed").poll(cx)
        assertEquals(Poll.ready("speed"), r2)

        val r3 = s2.nextIfEq("").poll(cx)
        assertEquals(Poll.ready(null), r3)
    }
}
