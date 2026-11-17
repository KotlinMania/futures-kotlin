// port-lint: tests futures/tests/future_try_flatten_stream.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FutureTryFlattenStreamTest {
    @Test
    fun successfulFuture() {
        val streamItems = listOf(17, 19)
        val st: TryStream<Int, Boolean> = streamIter(streamItems).map { Try.Ok(it) }.asTryStream()
        val futureOfStream: TryFuture<TryStream<Int, Boolean>, Boolean> =
            ok<TryStream<Int, Boolean>, Boolean>(st).asTryFuture()

        val stream = futureOfStream.tryFlattenStream()
        val cx = TaskContext()

        val item1 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Boolean>>>>(item1)
        assertEquals(Yield.value(Try.ok(17)), item1.value)

        val item2 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Boolean>>>>(item2)
        assertEquals(Yield.value(Try.ok(19)), item2.value)

        val end = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Int, Boolean>>>>(end)
        assertEquals(Yield.end(), end.value)
    }

    @Test
    fun failedFuture() {
        val futureOfStream: TryFuture<TryStream<Boolean, Int>, Int> =
            err<TryStream<Boolean, Int>, Int>(10).asTryFuture()
        val stream = futureOfStream.tryFlattenStream()
        val cx = TaskContext()

        val item1 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Boolean, Int>>>>(item1)
        assertEquals(Yield.value(Try.err(10)), item1.value)

        val end = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<Boolean, Int>>>>(end)
        assertEquals(Yield.end(), end.value)
    }
}
