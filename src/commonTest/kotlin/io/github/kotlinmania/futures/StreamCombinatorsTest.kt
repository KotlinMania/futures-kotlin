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
        val stream =
            listOf(1, 2, 3, 4, 5)
                .asStream()
                .filter { it % 2 != 0 }
                .map { it * 10 }

        val collected = stream.collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(collected)
        assertEquals(listOf(10, 30, 50), collected.value)
    }

    @Test
    fun filterMapAndFold() {
        val cx = TaskContext()
        val stream =
            listOf("1", "two", "3", "four", "5")
                .asStream()
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
        val stream =
            listOf(1, 2, 3, 4, 5, 6)
                .asStream()
                .skip(2)
                .takeWhile { it < 5 }

        val res = stream.collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(res)
        assertEquals(listOf(3, 4), res.value)
    }

    @Test
    fun skipWhileAndEnumerate() {
        val cx = TaskContext()
        val stream =
            listOf(1, 2, 3, 4, 5)
                .asStream()
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

        val once = streamOnce(ready(42)).collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(once)
        assertEquals(listOf(42), once.value)

        val rep = streamRepeat("x").take(3).collect().poll(cx)
        assertIs<Poll.Ready<List<String>>>(rep)
        assertEquals(listOf("x", "x", "x"), rep.value)

        var counter = 0
        val repWith = streamRepeatWith { ++counter }.take(3).collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(repWith)
        assertEquals(listOf(1, 2, 3), repWith.value)
    }

    @Test
    fun flattenStream() {
        val cx = TaskContext()
        val s1 = listOf(1, 2).asStream()
        val s2 = listOf(3, 4, 5).asStream()
        val outer = listOf(s1, s2).asStream()

        val flattened = outer.flatten().collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(flattened)
        assertEquals(listOf(1, 2, 3, 4, 5), flattened.value)
    }

    @Test
    fun unzipStream() {
        val cx = TaskContext()
        val stream = listOf(Pair("a", 1), Pair("b", 2), Pair("c", 3)).asStream()
        val unzipped = stream.unzip().poll(cx)
        assertIs<Poll.Ready<Pair<List<String>, List<Int>>>>(unzipped)
        assertEquals(listOf("a", "b", "c"), unzipped.value.first)
        assertEquals(listOf(1, 2, 3), unzipped.value.second)
    }

    @Test
    fun inspectStream() {
        val cx = TaskContext()
        val seen = mutableListOf<Int>()
        val stream = listOf(1, 2, 3).asStream().inspect { seen.add(it * 2) }

        val collected = stream.collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(collected)
        assertEquals(listOf(1, 2, 3), collected.value)
        assertEquals(listOf(2, 4, 6), seen)
    }

    @Test
    fun scanStream() {
        val cx = TaskContext()
        val stream =
            listOf(1, 2, 3, 4).asStream().scan(0) { state, item ->
                val sum = state + item
                Pair(sum, sum)
            }

        val res = stream.collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(res)
        assertEquals(listOf(1, 3, 6, 10), res.value)
    }

    @Test
    fun forEachStream() {
        val cx = TaskContext()
        val accumulated = mutableListOf<Int>()
        val future = listOf(10, 20, 30).asStream().forEach { accumulated.add(it) }

        val res = future.poll(cx)
        assertIs<Poll.Ready<Unit>>(res)
        assertEquals(listOf(10, 20, 30), accumulated)
    }

    @Test
    fun chunksStream() {
        val cx = TaskContext()
        val stream = listOf(1, 2, 3, 4, 5, 6, 7).asStream().chunks(3)

        val res = stream.collect().poll(cx)
        assertIs<Poll.Ready<List<List<Int>>>>(res)
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7)), res.value)
    }

    @Test
    fun takeUntilStream() {
        val cx = TaskContext()
        val (tx, rx) = unbounded<Int>()
        tx.unboundedSend(1)
        tx.unboundedSend(2)
        tx.unboundedSend(3)

        val stopFuture = Ready(Unit)
        val stream = rx.takeUntil(stopFuture)

        val p = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(p)
        assertEquals(Yield.end(), p.value)
    }

    @Test
    fun thenStream() {
        val cx = TaskContext()
        val stream = listOf(1, 2, 3).asStream().then { Ready(it * 10) }

        val res = stream.collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(res)
        assertEquals(listOf(10, 20, 30), res.value)
    }

    @Test
    fun cycleAndReadyChunks() {
        val cx = TaskContext()
        val cycled = { listOf(1, 2).asStream() }.cycle().take(5).collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(cycled)
        assertEquals(listOf(1, 2, 1, 2, 1), cycled.value)

        val readyChunked = listOf(1, 2, 3, 4).asStream().readyChunks(2).collect().poll(cx)
        assertIs<Poll.Ready<List<List<Int>>>>(readyChunked)
        assertEquals(listOf(listOf(1, 2), listOf(3, 4)), readyChunked.value)
    }

    @Test
    fun forEachConcurrentAndBuffered() {
        val cx = TaskContext()
        val seen = mutableListOf<Int>()
        val future = listOf(1, 2, 3).asStream().forEachConcurrent(2) {
            seen.add(it)
            Ready(Unit)
        }
        val p = future.poll(cx)
        assertIs<Poll.Ready<Unit>>(p)
        assertEquals(listOf(1, 2, 3), seen)

        val futuresStream = listOf(Ready(10), Ready(20), Ready(30)).asStream()
        val buffered = futuresStream.buffered(2).collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(buffered)
        assertEquals(listOf(10, 20, 30), buffered.value)

        val futuresStream2 = listOf(Ready(100), Ready(200)).asStream()
        val bufUnordered = futuresStream2.bufferUnordered(2).collect().poll(cx)
        assertIs<Poll.Ready<List<Int>>>(bufUnordered)
        assertEquals(listOf(100, 200), bufUnordered.value)
    }

    @Test
    fun sharedAndCatchUnwindAndRemoteHandle() {
        val cx = TaskContext()
        val okFut = Ready(42)
        val shared = okFut.shared()
        val s1 = shared.clone().poll(cx)
        assertIs<Poll.Ready<Int>>(s1)
        assertEquals(42, s1.value)
        val s2 = shared.poll(cx)
        assertIs<Poll.Ready<Int>>(s2)
        assertEquals(42, s2.value)

        val (remote, handle) = Ready("hello").remoteHandle()
        val pRemote = remote.poll(cx)
        assertIs<Poll.Ready<Unit>>(pRemote)
        val pHandle = handle.poll(cx)
        assertIs<Poll.Ready<String>>(pHandle)
        assertEquals("hello", pHandle.value)
    }
}

