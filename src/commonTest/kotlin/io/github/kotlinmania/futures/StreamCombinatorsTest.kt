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

    @Test
    fun mapAndFilter() {
        val cx = TaskContext()
        val stream = listOf(1, 2, 3, 4, 5).asStream()
            .filter { it % 2 != 0 }
            .map { it * 10 }

        val collected = stream.collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(collected)
        assertEquals(listOf(10, 30, 50), collected.value)
    }

    @Test
    fun filterMapAndFold() {
        val cx = TaskContext()
        val stream = listOf("1", "two", "3", "four", "5").asStream()
            .filterMap { it.toIntOrNull() }

        val foldFut = stream.fold(0) { acc, elem -> acc + elem }
        val sum = foldFut.poll(cx)
        assertIs<Poll.Ready<Int>>(sum)
        assertEquals(9, sum.value)
    }

    @Test
    fun countAndPredicates() {
        val cx = TaskContext()
        val stream = listOf(2, 4, 6, 8).asStream()
        val allEven = stream.forAll { it % 2 == 0 }.poll(cx)
        assertIs<Poll.Ready<Boolean>>(allEven)
        assertTrue(allEven.value)

        val anyGreaterThan5 = listOf(2, 4, 6, 8).asStream().any { it > 5 }.poll(cx)
        assertIs<Poll.Ready<Boolean>>(anyGreaterThan5)
        assertTrue(anyGreaterThan5.value)

        val cnt = listOf(10, 20, 30).asStream().count().poll(cx)
        assertIs<Poll.Ready<Int>>(cnt)
        assertEquals(3, cnt.value)
    }

    @Test
    fun skipAndTakeWhile() {
        val cx = TaskContext()
        val stream = listOf(1, 2, 3, 4, 5, 6).asStream()
            .skip(2)
            .takeWhile { it < 5 }

        val res = stream.collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(res)
        assertEquals(listOf(3, 4), res.value)
    }

    @Test
    fun skipWhileAndEnumerate() {
        val cx = TaskContext()
        val stream = listOf(1, 2, 3, 4, 5).asStream()
            .skipWhile { it < 4 }
            .enumerate()

        val res = stream.collect().poll(cx)
        assertIs<Poll.Ready<List<IndexedValue<Int>>>>(res)
        assertEquals(listOf(IndexedValue(0, 4), IndexedValue(1, 5)), res.value)
    }

    @Test
    fun chainAndZip() {
        val cx = TaskContext()
        val s1 = listOf(1, 2).asStream()
        val s2 = listOf(3, 4).asStream()
        val chained = s1.chain(s2).collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(chained)
        assertEquals(listOf(1, 2, 3, 4), chained.value)

        val letters = listOf("a", "b", "c").asStream()
        val numbers = listOf(1, 2).asStream()
        val zipped = letters.zip(numbers).collect().poll(cx)
        assertIs<Poll.Ready<List<Pair<String, Int>>>>(zipped)
        assertEquals(listOf(Pair("a", 1), Pair("b", 2)), zipped.value)
    }

    @Test
    fun factoryStreams() {
        val cx = TaskContext()
        val empty = emptyStream<Int>().collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(empty)
        assertEquals(emptyList(), empty.value)

        val once = onceStream(Ready(42)).collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(once)
        assertEquals(listOf(42), once.value)

        val rep = repeatStream("x").take(3).collect().poll(cx)
        assertIs<Poll.Ready<List<String>>>(rep)
        assertEquals(listOf("x", "x", "x"), rep.value)

        var counter = 0
        val repWith = repeatWithStream { ++counter }.take(3).collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(repWith)
        assertEquals(listOf(1, 2, 3), repWith.value)
    }
}
