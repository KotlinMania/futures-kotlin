// port-lint: tests futures/tests/stream_futures_ordered.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.channel.oneshot.Canceled
import io.github.kotlinmania.futures.channel.oneshot.oneshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FuturesOrderedTest {
    @Test
    fun works1() {
        val (aTx, aRx) = oneshot<Int>()
        val (bTx, bRx) = oneshot<Int>()
        val (cTx, cRx) = oneshot<Int>()

        val stream = listOf(aRx, bRx, cRx).collectFuturesOrdered()
        val cx = TaskContext()

        bTx.send(99)
        val p1 = stream.pollNext(cx)
        assertIs<Poll.Pending>(p1)

        aTx.send(33)
        cTx.send(33)

        val r1 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(r1)
        assertEquals(Yield.value(Try.ok(33)), r1.value)

        val r2 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(r2)
        assertEquals(Yield.value(Try.ok(99)), r2.value)

        val r3 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(r3)
        assertEquals(Yield.value(Try.ok(33)), r3.value)

        val r4 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(r4)
        assertEquals(Yield.end(), r4.value)
    }

    @Test
    fun works2() {
        val (aTx, aRx) = oneshot<Int>()
        val (bTx, bRx) = oneshot<Int>()
        val (cTx, cRx) = oneshot<Int>()

        val joined =
            join(bRx, cRx).map { (b, c) ->
                (b as Try.Ok).value + (c as Try.Ok).value
            }

        val aMapped =
            aRx.map {
                (it as Try.Ok).value
            }

        val stream = listOf(aMapped, joined).collectFuturesOrdered()
        val cx = TaskContext()

        aTx.send(33)
        bTx.send(33)

        val p1 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(p1)
        assertEquals(Yield.value(33), p1.value)

        val p2 = stream.pollNext(cx)
        assertIs<Poll.Pending>(p2)

        cTx.send(33)

        val p3 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(p3)
        assertEquals(Yield.value(66), p3.value)
    }

    @Test
    fun testPushFront() {
        val (aTx, aRx) = oneshot<Int>()
        val (bTx, bRx) = oneshot<Int>()
        val (cTx, cRx) = oneshot<Int>()
        val (dTx, dRx) = oneshot<Int>()

        val stream = FuturesOrdered<Try<Int, Canceled>>()
        val cx = TaskContext()

        stream.pushBack(aRx)
        stream.pushBack(bRx)
        stream.pushBack(cRx)

        aTx.send(1)
        bTx.send(2)
        cTx.send(3)

        val r1 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(r1)
        assertEquals(Yield.value(Try.ok(1)), r1.value)

        val r2 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(r2)
        assertEquals(Yield.value(Try.ok(2)), r2.value)

        stream.pushFront(dRx)
        dTx.send(4)

        val r3 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(r3)
        assertEquals(Yield.value(Try.ok(4)), r3.value)

        val r4 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(r4)
        assertEquals(Yield.value(Try.ok(3)), r4.value)
    }

    @Test
    fun testPushBack() {
        val (aTx, aRx) = oneshot<Int>()
        val (bTx, bRx) = oneshot<Int>()
        val (cTx, cRx) = oneshot<Int>()
        val (dTx, dRx) = oneshot<Int>()

        val stream = FuturesOrdered<Try<Int, Canceled>>()
        val cx = TaskContext()

        stream.pushBack(aRx)
        stream.pushBack(bRx)
        stream.pushBack(cRx)

        aTx.send(1)
        bTx.send(2)
        cTx.send(3)

        val r1 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(r1)
        assertEquals(Yield.value(Try.ok(1)), r1.value)

        val r2 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(r2)
        assertEquals(Yield.value(Try.ok(2)), r2.value)

        stream.pushBack(dRx)
        dTx.send(4)

        val r3 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(r3)
        assertEquals(Yield.value(Try.ok(3)), r3.value)

        val r4 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(r4)
        assertEquals(Yield.value(Try.ok(4)), r4.value)
    }

    @Test
    fun testPushFrontNegative() {
        val (aTx, aRx) = oneshot<Int>()
        val (bTx, bRx) = oneshot<Int>()
        val (cTx, cRx) = oneshot<Int>()

        val stream = FuturesOrdered<Try<Int, Canceled>>()
        val cx = TaskContext()

        stream.pushFront(aRx)
        stream.pushFront(bRx)
        stream.pushFront(cRx)

        aTx.send(1)
        bTx.send(2)
        cTx.send(3)

        // Reverse order of pushFront
        val r1 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(r1)
        assertEquals(Yield.value(Try.ok(3)), r1.value)

        val r2 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(r2)
        assertEquals(Yield.value(Try.ok(2)), r2.value)

        val r3 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Canceled>>>>(r3)
        assertEquals(Yield.value(Try.ok(1)), r3.value)
    }

    @Test
    fun fromIterator() {
        val stream = listOf(ready(1), ready(2), ready(3)).collectFuturesOrdered()
        assertEquals(3, stream.len())
        val cx = TaskContext()
        val collected = stream.collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(collected)
        assertEquals(listOf(1, 2, 3), collected.value)
    }

    @Test
    fun bufferedStream() {
        val (tx, rx) =
            io.github.kotlinmania.futures.channel.mpsc
                .unbounded<Future<Int>>()
        val cx = TaskContext()

        tx.unboundedSend(ready(10))
        tx.unboundedSend(ready(20))
        tx.unboundedSend(ready(30))
        tx.disconnect()

        val buffered = rx.buffered(2)
        val collected = buffered.collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(collected)
        assertEquals(listOf(10, 20, 30), collected.value)
    }

    @Test
    fun queueNeverUnblocked() {
        val (_aTx, aRx) = oneshot<Any>()
        val (bTx, bRx) = oneshot<Any>()
        val (cTx, cRx) = oneshot<Any>()

        val selected =
            select(bRx, cRx).map { either ->
                when (either) {
                    is Either.Left -> either.value
                    is Either.Right -> either.value
                }
            }

        val stream = listOf(aRx, selected).collectFuturesOrdered()
        val cx = TaskContext()

        for (i in 0 until 10) {
            assertIs<Poll.Pending>(stream.pollNext(cx))
        }

        bTx.send(Unit)
        assertIs<Poll.Pending>(stream.pollNext(cx))
        cTx.send(Unit)
        assertIs<Poll.Pending>(stream.pollNext(cx))
        assertIs<Poll.Pending>(stream.pollNext(cx))
    }
}
