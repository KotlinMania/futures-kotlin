// port-lint: tests futures-util/src/stream/mod.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.channel.mpsc.unbounded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamCombinatorsTest {
    @Test
    fun collectStream() {
        val (tx, rx) = unbounded<Int>()
        val cx = TaskContext()

        tx.unboundedSend(1)
        tx.unboundedSend(2)
        tx.unboundedSend(3)
        tx.disconnect()

        val collectFuture = rx.collect()
        val res = collectFuture.poll(cx)
        assertIs<Poll.Ready<List<Int>>>(res)
        assertEquals(listOf(1, 2, 3), res.value)
    }

    @Test
    fun takeStream() {
        val (tx, rx) = unbounded<Int>()
        val cx = TaskContext()

        tx.unboundedSend(10)
        tx.unboundedSend(20)
        tx.unboundedSend(30)
        tx.unboundedSend(40)

        val takeStream = rx.take(2)
        val p1 = takeStream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(p1)
        assertEquals(Yield.value(10), p1.value)

        val p2 = takeStream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(p2)
        assertEquals(Yield.value(20), p2.value)

        val p3 = takeStream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(p3)
        assertEquals(Yield.end(), p3.value)
    }

    @Test
    fun nextFuture() {
        val (tx, rx) = unbounded<Int>()
        val cx = TaskContext()

        tx.unboundedSend(99)
        val n1 = rx.next().poll(cx)
        assertIs<Poll.Ready<Int?>>(n1)
        assertEquals(99, n1.value)

        tx.disconnect()
        val n2 = rx.next().poll(cx)
        assertIs<Poll.Ready<Int?>>(n2)
        assertNull(n2.value)
    }

    @Test
    fun sinkSendAndFlush() {
        val list = mutableListOf<Int>()
        val sink = list.asSink()
        val cx = TaskContext()

        val sendFut = sink.send(42)
        val p = sendFut.poll(cx)
        assertIs<Poll.Ready<Try<Unit, Nothing>>>(p)
        assertTrue(p.value is Try.Ok)
        assertEquals(listOf(42), list)

        val flushFut = sink.flush()
        val pf = flushFut.poll(cx)
        assertIs<Poll.Ready<Try<Unit, Nothing>>>(pf)

        val closeFut = sink.close()
        val pc = closeFut.poll(cx)
        assertIs<Poll.Ready<Try<Unit, Nothing>>>(pc)
    }
}
