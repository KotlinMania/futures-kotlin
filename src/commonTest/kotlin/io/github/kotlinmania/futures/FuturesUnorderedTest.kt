// port-lint: tests futures/tests/stream_futures_unordered.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.channel.oneshot.Canceled
import io.github.kotlinmania.futures.channel.oneshot.oneshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FuturesUnorderedTest {
    @Test
    fun isTerminated() {
        val cx = TaskContext()
        val tasks = FuturesUnordered<Int>()

        assertFalse(tasks.isTerminated())
        val p1 = tasks.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(p1)
        assertEquals(Yield.end(), p1.value)
        assertTrue(tasks.isTerminated())

        assertTrue(tasks.isEmpty())
        assertEquals(0, tasks.len())

        tasks.push(ready(1))

        assertFalse(tasks.isEmpty())
        assertEquals(1, tasks.len())
        assertFalse(tasks.isTerminated())

        val p2 = tasks.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(p2)
        assertEquals(Yield.value(1), p2.value)
        assertFalse(tasks.isTerminated())

        val p3 = tasks.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(p3)
        assertEquals(Yield.end(), p3.value)
        assertTrue(tasks.isTerminated())
    }

    @Test
    fun works1() {
        val (aTx, aRx) = oneshot<Int>()
        val (bTx, bRx) = oneshot<Int>()
        val (cTx, cRx) = oneshot<Int>()

        val tasks = listOf(aRx, bRx, cRx).collectFuturesUnordered()
        val cx = TaskContext()

        bTx.send(99)
        val p1 = tasks.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(p1)
        val y1 = p1.value
        assertIs<Yield.Value<Try<Int, Canceled>>>(y1)
        assertEquals(Try.ok(99), y1.value)

        aTx.send(33)
        cTx.send(33)

        val p2 = tasks.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(p2)
        val y2 = p2.value
        assertIs<Yield.Value<Try<Int, Canceled>>>(y2)
        assertEquals(Try.ok(33), y2.value)

        val p3 = tasks.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(p3)
        val y3 = p3.value
        assertIs<Yield.Value<Try<Int, Canceled>>>(y3)
        assertEquals(Try.ok(33), y3.value)

        val p4 = tasks.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(p4)
        assertEquals(Yield.end(), p4.value)
    }

    @Test
    fun works2() {
        val (aTx, aRx) = oneshot<Int>()
        val (bTx, bRx) = oneshot<Int>()
        val (cTx, cRx) = oneshot<Int>()

        val joined = join(bRx, cRx).map { (b, c) ->
            (b as Try.Ok).value + (c as Try.Ok).value
        }

        val aMapped = aRx.map {
            (it as Try.Ok).value
        }

        val stream = listOf(aMapped, joined).collectFuturesUnordered()
        val cx = TaskContext()

        aTx.send(9)
        bTx.send(10)

        val p1 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(p1)
        assertEquals(Yield.value(9), p1.value)

        val p2 = stream.pollNext(cx)
        assertIs<Poll.Pending>(p2)

        cTx.send(20)

        val p3 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(p3)
        assertEquals(Yield.value(30), p3.value)

        val p4 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(p4)
        assertEquals(Yield.end(), p4.value)
    }

    @Test
    fun fromIterator() {
        val stream = listOf(ready(1), ready(2), ready(3)).collectFuturesUnordered()
        assertEquals(3, stream.len())
        val cx = TaskContext()
        val collected = stream.collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(collected)
        assertEquals(3, collected.value.size)
        assertTrue(collected.value.containsAll(listOf(1, 2, 3)))
    }

    @Test
    fun bufferUnorderedStream() {
        val (tx, rx) = io.github.kotlinmania.futures.channel.mpsc.unbounded<Future<Int>>()
        val cx = TaskContext()

        tx.unboundedSend(ready(10))
        tx.unboundedSend(ready(20))
        tx.unboundedSend(ready(30))
        tx.disconnect()

        val buffered = rx.bufferUnordered(2)
        val collected = buffered.collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(collected)
        assertEquals(listOf(10, 20, 30), collected.value)
    }
}
