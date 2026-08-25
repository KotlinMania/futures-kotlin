// port-lint: tests futures/tests/future_try_flatten_stream.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FlattenTest {
    @Test
    fun flattenFutureSuccess() {
        val innerFuture: Future<Int> = ready(42)
        val outerFuture: Future<Future<Int>> = ready(innerFuture)
        val flattened = outerFuture.flatten()

        val cx = TaskContext()
        assertFalse(flattened.isTerminated())
        val res = flattened.poll(cx)
        assertIs<Poll.Ready<Int>>(res)
        assertEquals(42, res.value)
        assertTrue(flattened.isTerminated())
    }

    @Test
    fun flattenStreamSuccess() {
        val stream: Stream<Int> = streamIter(listOf(1, 2, 3))
        val outerFuture: Future<Stream<Int>> = ready(stream)
        val flattened = outerFuture.flattenStream()

        val cx = TaskContext()
        assertFalse(flattened.isTerminated())

        val item1 = flattened.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(item1)
        assertEquals(Yield.value(1), item1.value)

        val item2 = flattened.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(item2)
        assertEquals(Yield.value(2), item2.value)

        val item3 = flattened.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(item3)
        assertEquals(Yield.value(3), item3.value)

        val end = flattened.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(end)
        assertEquals(Yield.end(), end.value)
        assertTrue(flattened.isTerminated())
    }

    @Test
    fun flattenSinkSuccess() {
        val sink = Drain<Int>()
        val outerFuture: Future<Sink<Int, Nothing>> = ready(sink)
        val flattened = outerFuture.flattenSink()

        val cx = TaskContext()
        val readyRes = flattened.pollReady(cx)
        assertIs<Poll.Ready<SinkOutcome<Nothing>>>(readyRes)
        assertEquals(SinkOutcome.ready(), readyRes.value)

        val sendRes = flattened.startSend(10)
        assertEquals(SinkOutcome.ready(), sendRes)

        val flushRes = flattened.pollFlush(cx)
        assertIs<Poll.Ready<SinkOutcome<Nothing>>>(flushRes)

        val closeRes = flattened.pollClose(cx)
        assertIs<Poll.Ready<SinkOutcome<Nothing>>>(closeRes)
    }

    @Test
    fun tryFlattenFutureSuccess() {
        val innerTryFuture: TryFuture<Int, String> = ready(Try.ok<Int>(99)).asTryFuture()
        val outerTryFuture: TryFuture<TryFuture<Int, String>, String> = ready(Try.ok<TryFuture<Int, String>>(innerTryFuture)).asTryFuture()
        val flattened = outerTryFuture.tryFlatten()

        val cx = TaskContext()
        assertFalse(flattened.isTerminated())
        val res = flattened.poll(cx)
        assertIs<Poll.Ready<Try<Int, String>>>(res)
        assertEquals(Try.ok(99), res.value)
        assertTrue(flattened.isTerminated())
    }

    @Test
    fun tryFlattenFutureOuterError() {
        val outerTryFuture: TryFuture<TryFuture<Int, String>, String> = ready(Try.err<String>("outer error")).asTryFuture()
        val flattened = outerTryFuture.tryFlatten()

        val cx = TaskContext()
        assertFalse(flattened.isTerminated())
        val res = flattened.poll(cx)
        assertIs<Poll.Ready<Try<Int, String>>>(res)
        assertEquals(Try.err("outer error"), res.value)
        assertTrue(flattened.isTerminated())
    }

    @Test
    fun tryFlattenStreamSuccessfulFuture() {
        val streamItems: TryStream<Int, Boolean> = streamIter(listOf(17, 19)).map { Try.ok<Int>(it) }.asTryStream()
        val futureOfAStream: TryFuture<TryStream<Int, Boolean>, Boolean> = ready(Try.ok<TryStream<Int, Boolean>>(streamItems)).asTryFuture()
        val stream = futureOfAStream.tryFlattenStream()

        val cx = TaskContext()
        val item1 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Boolean>>>>(item1)
        assertEquals(Yield.value(Try.ok(17)), item1.value)

        val item2 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Boolean>>>>(item2)
        assertEquals(Yield.value(Try.ok(19)), item2.value)

        val item3 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Boolean>>>>(item3)
        assertEquals(Yield.end(), item3.value)
    }

    @Test
    fun tryFlattenStreamFailedFuture() {
        val futureOfAStream: TryFuture<TryStream<Boolean, Int>, Int> = ready(Try.err<Int>(10)).asTryFuture()
        val stream = futureOfAStream.tryFlattenStream()

        val cx = TaskContext()
        val item1 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Boolean, Int>>>>(item1)
        assertEquals(Yield.value(Try.err(10)), item1.value)

        val item2 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Boolean, Int>>>>(item2)
        assertEquals(Yield.end(), item2.value)
    }
}
