// port-lint: tests futures/tests/stream_select_all.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.channel.mpsc.unbounded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamSelectAllTest {
    @Test
    fun testSelectAllStream() {
        val s1 = streamIter(listOf(1, 3))
        val s2 = streamIter(listOf(2, 4))
        val select = streamSelectAll(listOf(s1, s2))
        val cx = TaskContext()

        val results = mutableListOf<Int>()
        while (true) {
            val p = select.pollNext(cx)
            assertTrue(p is Poll.Ready)
            when (val y = p.value) {
                is Yield.Value -> results.add(y.value)
                Yield.End -> break
            }
        }
        assertEquals(4, results.size)
        assertTrue(results.containsAll(listOf(1, 2, 3, 4)))
    }

    @Test
    fun isTerminated() {
        val cx = TaskContext()
        val tasks = SelectAllStream<Int>()

        assertFalse(tasks.isTerminated())
        assertEquals(Poll.ready(Yield.end()), tasks.pollNext(cx))
        assertTrue(tasks.isTerminated())

        assertTrue(tasks.isEmpty())
        assertEquals(0, tasks.len())

        tasks.push(streamIter(listOf(1)))

        assertFalse(tasks.isEmpty())
        assertEquals(1, tasks.len())

        assertFalse(tasks.isTerminated())
        assertEquals(Poll.ready(Yield.value(1)), tasks.pollNext(cx))
        assertFalse(tasks.isTerminated())
        assertEquals(Poll.ready(Yield.end()), tasks.pollNext(cx))
        assertTrue(tasks.isTerminated())
    }

    @Test
    fun issue1626() {
        val a = streamIter((0..2).toList())
        val b = streamIter((10..14).toList())

        val s = streamSelectAll(listOf(a, b))
        val cx = TaskContext()

        val items = mutableListOf<Int>()
        while (true) {
            val p = s.pollNext(cx)
            when (val y = (p as Poll.Ready).value) {
                is Yield.Value -> items.add(y.value)
                Yield.End -> break
            }
        }
        assertEquals(listOf(0, 10, 1, 11, 2, 12, 13, 14), items)
    }

    @Test
    fun works1() {
        val (aTx, aRx) = unbounded<Int>()
        val (bTx, bRx) = unbounded<Int>()
        val (cTx, cRx) = unbounded<Int>()

        val s = streamSelectAll(listOf(aRx, bRx, cRx))
        val cx = TaskContext()

        bTx.unboundedSend(99)
        aTx.unboundedSend(33)
        assertEquals(Poll.ready(Yield.value(33)), s.pollNext(cx))
        assertEquals(Poll.ready(Yield.value(99)), s.pollNext(cx))

        bTx.unboundedSend(99)
        aTx.unboundedSend(33)
        assertEquals(Poll.ready(Yield.value(33)), s.pollNext(cx))
        assertEquals(Poll.ready(Yield.value(99)), s.pollNext(cx))

        cTx.unboundedSend(42)
        assertEquals(Poll.ready(Yield.value(42)), s.pollNext(cx))
        aTx.unboundedSend(43)
        assertEquals(Poll.ready(Yield.value(43)), s.pollNext(cx))

        aTx.disconnect()
        bTx.disconnect()
        cTx.disconnect()
        assertEquals(Poll.ready(Yield.end()), s.pollNext(cx))
    }

    @Test
    fun clear() {
        val tasks = streamSelectAll(listOf(streamIter(listOf(1)), streamIter(listOf(2))))
        val cx = TaskContext()

        assertEquals(Poll.ready(Yield.value(1)), tasks.pollNext(cx))
        assertFalse(tasks.isEmpty())

        tasks.clear()
        assertTrue(tasks.isEmpty())

        tasks.push(streamIter(listOf(3)))
        assertFalse(tasks.isEmpty())

        tasks.clear()
        assertTrue(tasks.isEmpty())

        assertEquals(Poll.ready(Yield.end()), tasks.pollNext(cx))
        assertTrue(tasks.isTerminated())
        tasks.clear()
        assertFalse(tasks.isTerminated())
    }

    @Test
    fun iter() {
        val stream = SelectAllStream<Unit>()
        stream.push(pendingStream())
        stream.push(pendingStream())
        stream.push(pendingStream())

        val iter1 = stream.iter()
        assertEquals(3, iter1.size)

        val s2 = SelectAllStream<Int>()
        s2.push(streamIter(emptyList()))
        s2.push(streamIter(listOf(1)))
        s2.push(streamIter(listOf(2)))

        assertEquals(3, s2.len())
        val cx = TaskContext()
        assertEquals(Poll.ready(Yield.value(1)), s2.pollNext(cx))
        assertEquals(2, s2.len())
        assertEquals(2, s2.iter().size)

        assertEquals(Poll.ready(Yield.value(2)), s2.pollNext(cx))
        assertEquals(2, s2.len())
        assertEquals(Poll.ready(Yield.end()), s2.pollNext(cx))
        assertEquals(0, s2.iter().size)
    }
}
