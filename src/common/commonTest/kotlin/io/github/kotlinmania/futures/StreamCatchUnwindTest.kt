// port-lint: tests futures/tests/stream_catch_unwind.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StreamCatchUnwindTest {
    @Test
    fun panicInTheMiddleOfTheStream() {
        val cx = TaskContext()
        val items = listOf<Int?>(10, null, 11)
        val stream =
            items.asStream().map { opt ->
                opt ?: throw IllegalStateException("panic on null element")
            }

        val caughtStream = stream.catchUnwind()

        val p1 = caughtStream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Result<Int>>>>(p1)
        val y1 = p1.value
        assertIs<Yield.Value<Result<Int>>>(y1)
        assertTrue(y1.value.isSuccess)
        assertEquals(10, y1.value.getOrThrow())

        val p2 = caughtStream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Result<Int>>>>(p2)
        val y2 = p2.value
        assertIs<Yield.Value<Result<Int>>>(y2)
        assertTrue(y2.value.isFailure)

        val p3 = caughtStream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Result<Int>>>>(p3)
        assertEquals(Yield.End, p3.value)
    }

    @Test
    fun noPanic() {
        val cx = TaskContext()
        val stream = listOf(10, 11, 12).asStream()
        val caughtStream = stream.catchUnwind()

        val p1 = caughtStream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Result<Int>>>>(p1)
        val y1 = p1.value
        assertIs<Yield.Value<Result<Int>>>(y1)
        assertTrue(y1.value.isSuccess)
        assertEquals(10, y1.value.getOrThrow())

        val p2 = caughtStream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Result<Int>>>>(p2)
        val y2 = p2.value
        assertIs<Yield.Value<Result<Int>>>(y2)
        assertTrue(y2.value.isSuccess)
        assertEquals(11, y2.value.getOrThrow())

        val p3 = caughtStream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Result<Int>>>>(p3)
        val y3 = p3.value
        assertIs<Yield.Value<Result<Int>>>(y3)
        assertTrue(y3.value.isSuccess)
        assertEquals(12, y3.value.getOrThrow())

        val p4 = caughtStream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Result<Int>>>>(p4)
        assertEquals(Yield.End, p4.value)
    }
}
