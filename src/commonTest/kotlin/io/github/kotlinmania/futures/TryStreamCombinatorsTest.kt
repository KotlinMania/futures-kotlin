// port-lint: tests stream_try_stream.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TryStreamCombinatorsTest {
    @Test
    fun testTryChunks() {
        val stream = streamIter(listOf(Try.ok(1), Try.ok(2), Try.ok(3), Try.ok(4), Try.ok(5))).asTryStream()
        val chunked = stream.tryChunks(2)
        val context = TaskContext()

        val p1 = chunked.pollNext(context)
        assertTrue(p1 is Poll.Ready)
        assertEquals(Yield.Value(Try.ok(listOf(1, 2))), p1.value)

        val p2 = chunked.pollNext(context)
        assertTrue(p2 is Poll.Ready)
        assertEquals(Yield.Value(Try.ok(listOf(3, 4))), p2.value)

        val p3 = chunked.pollNext(context)
        assertTrue(p3 is Poll.Ready)
        assertEquals(Yield.Value(Try.ok(listOf(5))), p3.value)

        val p4 = chunked.pollNext(context)
        assertTrue(p4 is Poll.Ready)
        assertEquals(Yield.End, p4.value)
    }

    @Test
    fun testTryChunksWithError() {
        val stream = streamIter(listOf(Try.ok(1), Try.ok(2), Try.err("boom"), Try.ok(3))).asTryStream()
        val chunked = stream.tryChunks(3)
        val context = TaskContext()

        val p1 = chunked.pollNext(context)
        assertTrue(p1 is Poll.Ready)
        val v1 = p1.value
        assertTrue(v1 is Yield.Value)
        val t1 = v1.value
        assertTrue(t1 is Try.Err)
        assertEquals(TryChunksError(listOf(1, 2), "boom"), t1.error)
    }

    @Test
    fun testTryReadyChunks() {
        val stream = streamIter(listOf(Try.ok(10), Try.ok(20), Try.ok(30))).asTryStream()
        val readyChunked = stream.tryReadyChunks(2)
        val context = TaskContext()

        val p1 = readyChunked.pollNext(context)
        assertTrue(p1 is Poll.Ready)
        assertEquals(Yield.Value(Try.ok(listOf(10, 20))), p1.value)

        val p2 = readyChunked.pollNext(context)
        assertTrue(p2 is Poll.Ready)
        assertEquals(Yield.Value(Try.ok(listOf(30))), p2.value)

        val p3 = readyChunked.pollNext(context)
        assertTrue(p3 is Poll.Ready)
        assertEquals(Yield.End, p3.value)
    }

    @Test
    fun testTryReadyChunksError() {
        val stream = streamIter(listOf(Try.ok(10), Try.err("err"))).asTryStream()
        val readyChunked = stream.tryReadyChunks(5)
        val context = TaskContext()

        val p1 = readyChunked.pollNext(context)
        assertTrue(p1 is Poll.Ready)
        val v1 = p1.value
        assertTrue(v1 is Yield.Value)
        val t1 = v1.value
        assertTrue(t1 is Try.Err)
        assertEquals(TryReadyChunksError(listOf(10), "err"), t1.error)
    }

    @Test
    fun testTryBuffered() {
        val futures = listOf(
            Try.ok(ready(Try.ok(1))),
            Try.ok(ready(Try.ok(2))),
            Try.ok(ready(Try.ok(3))),
        )
        val stream = streamIter(futures).asTryStream().tryBuffered(2)
        val context = TaskContext()

        val p1 = stream.pollNext(context)
        assertTrue(p1 is Poll.Ready)
        assertEquals(Yield.Value(Try.ok(1)), p1.value)

        val p2 = stream.pollNext(context)
        assertTrue(p2 is Poll.Ready)
        assertEquals(Yield.Value(Try.ok(2)), p2.value)

        val p3 = stream.pollNext(context)
        assertTrue(p3 is Poll.Ready)
        assertEquals(Yield.Value(Try.ok(3)), p3.value)

        val p4 = stream.pollNext(context)
        assertTrue(p4 is Poll.Ready)
        assertEquals(Yield.End, p4.value)
    }

    @Test
    fun testTryBufferUnordered() {
        val futures = listOf(
            Try.ok(ready(Try.ok(10))),
            Try.ok(ready(Try.ok(20))),
        )
        val stream = streamIter(futures).asTryStream().tryBufferUnordered(2)
        val context = TaskContext()

        val p1 = stream.pollNext(context)
        assertTrue(p1 is Poll.Ready)
        assertEquals(Yield.Value(Try.ok(10)), p1.value)

        val p2 = stream.pollNext(context)
        assertTrue(p2 is Poll.Ready)
        assertEquals(Yield.Value(Try.ok(20)), p2.value)

        val p3 = stream.pollNext(context)
        assertTrue(p3 is Poll.Ready)
        assertEquals(Yield.End, p3.value)
    }

    @Test
    fun testTryForEachConcurrent() {
        val collected = mutableListOf<Int>()
        val stream = streamIter(listOf(Try.ok(1), Try.ok(2), Try.ok(3))).asTryStream()
        val fut = stream.tryForEachConcurrent(limit = 2) { item ->
            collected.add(item)
            ready(Try.ok(Unit))
        }
        val context = TaskContext()
        val res = fut.poll(context)
        assertTrue(res is Poll.Ready)
        assertEquals(Try.ok(Unit), res.value)
        assertEquals(listOf(1, 2, 3), collected)
    }

    @Test
    fun testTryForEachConcurrentError() {
        val stream = streamIter(listOf(Try.ok(1), Try.err("failed"))).asTryStream()
        val fut = stream.tryForEachConcurrent {
            ready(Try.ok(Unit))
        }
        val context = TaskContext()
        val res = fut.poll(context)
        assertTrue(res is Poll.Ready)
        assertEquals(Try.err("failed"), res.value)
    }

    @Test
    fun testInspectOkAndErr() {
        var okVal: Int? = null
        var errVal: String? = null

        val okStream = streamIter(listOf(Try.ok(42))).asTryStream()
            .inspectOk { okVal = it }
        val context = TaskContext()
        okStream.pollNext(context)
        assertEquals(42, okVal)

        val errStream = streamIter(listOf(Try.err("boom"))).asTryStream()
            .inspectErr { errVal = it }
        errStream.pollNext(context)
        assertEquals("boom", errVal)
    }
}
