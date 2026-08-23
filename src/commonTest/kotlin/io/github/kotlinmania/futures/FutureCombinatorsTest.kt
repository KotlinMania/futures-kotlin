// port-lint: tests futures-util/src/future/mod.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FutureCombinatorsTest {
    @Test
    fun testReadyAndPending() {
        val readyFut = ready(42)
        val pollReady = readyFut.poll(TaskContext())
        assertTrue(pollReady is Poll.Ready)
        assertEquals(42, pollReady.value)

        val pendingFut = pending<Int>()
        val pollPending = pendingFut.poll(TaskContext())
        assertTrue(pollPending is Poll.Pending)
    }

    @Test
    fun testPollFn() {
        var count = 0
        val fut =
            pollFn {
                count++
                if (count >= 2) Poll.Ready("done") else Poll.Pending
            }

        assertTrue(fut.poll(TaskContext()) is Poll.Pending)
        val second = fut.poll(TaskContext())
        assertTrue(second is Poll.Ready)
        assertEquals("done", second.value)
    }

    @Test
    fun testJoinAndJoin3() {
        val f1 = ready(10)
        val f2 = ready("hello")
        val joined = join(f1, f2)
        val res = joined.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        assertEquals(Pair(10, "hello"), res.value)

        val joined3 = join3(ready(10), ready("hello"), ready(true))
        val res3 = joined3.poll(TaskContext())
        assertTrue(res3 is Poll.Ready)
        assertEquals(Triple(10, "hello", true), res3.value)
    }

    @Test
    fun testJoinAll() {
        val list = listOf(ready(1), ready(2), ready(3))
        val joined = joinAll(list)
        val res = joined.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        assertEquals(listOf(1, 2, 3), res.value)
    }

    @Test
    fun testMap() {
        val f = ready(21).map { it * 2 }
        val res = f.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        assertEquals(42, res.value)
    }

    @Test
    fun testFuse() {
        val f = ready(2).fuse()
        assertFalse(f.isTerminated())
        val p1 = f.poll(TaskContext())
        assertTrue(p1 is Poll.Ready)
        assertEquals(2, p1.value)
        assertTrue(f.isTerminated())
        assertTrue(f.poll(TaskContext()) is Poll.Pending)

        val terminated = Fuse.terminated<Int>()
        assertTrue(terminated.isTerminated())
        assertTrue(terminated.poll(TaskContext()) is Poll.Pending)
    }

    @Test
    fun testInspect() {
        var inspected = 0
        val f = ready(40).inspect { inspected += it }
        val p = f.poll(TaskContext())
        assertTrue(p is Poll.Ready)
        assertEquals(40, p.value)
        assertEquals(40, inspected)
    }

    @Test
    fun testThenAndFlatten() {
        val f = ready(10).then { ready(it * 3) }
        val p = f.poll(TaskContext())
        assertTrue(p is Poll.Ready)
        assertEquals(30, p.value)

        val nested = ready(ready("nested"))
        val flattened = nested.flatten()
        val pFlat = flattened.poll(TaskContext())
        assertTrue(pFlat is Poll.Ready)
        assertEquals("nested", pFlat.value)
    }

    @Test
    fun testUnitErrorAndNeverError() {
        val f1 = ready(99).unitError()
        val p1 = f1.poll(TaskContext())
        assertTrue(p1 is Poll.Ready && p1.value is Try.Ok)
        assertEquals(99, p1.value.value)

        val f2 = ready("ok").neverError()
        val p2 = f2.poll(TaskContext())
        assertTrue(p2 is Poll.Ready && p2.value is Try.Ok)
        assertEquals("ok", p2.value.value)
    }

    @Test
    fun testTryFutureCombinators() {
        val mappedOk = ready(Try.ok(10)).asTryFuture().mapOk { it * 2 }
        val pMapOk = mappedOk.poll(TaskContext())
        assertTrue(pMapOk is Poll.Ready && pMapOk.value is Try.Ok)
        assertEquals(20, pMapOk.value.value)

        val mappedErr = ready(Try.err("fail")).asTryFuture().mapErr { "error: $it" }
        val pMapErr = mappedErr.poll(TaskContext())
        assertTrue(pMapErr is Poll.Ready && pMapErr.value is Try.Err)
        assertEquals("error: fail", pMapErr.value.error)

        val chainedOk = ready(Try.ok(10)).asTryFuture().andThen { ready(Try.ok(it + 5)).asTryFuture() }
        val pChainedOk = chainedOk.poll(TaskContext())
        assertTrue(pChainedOk is Poll.Ready && pChainedOk.value is Try.Ok)
        assertEquals(15, pChainedOk.value.value)

        val recovered = ready(Try.err("fail")).asTryFuture().orElse { ready(Try.ok(999)).asTryFuture() }
        val pRecovered = recovered.poll(TaskContext())
        assertTrue(pRecovered is Poll.Ready && pRecovered.value is Try.Ok)
        assertEquals(999, pRecovered.value.value)

        val unwrapped = ready(Try.err("fail")).asTryFuture().unwrapOrElse { 0 }
        val pUnwrapped = unwrapped.poll(TaskContext())
        assertTrue(pUnwrapped is Poll.Ready)
        assertEquals(0, pUnwrapped.value)

        var inspectOkVal = 0
        ready(Try.ok(10)).asTryFuture().inspectOk { inspectOkVal = it }.poll(TaskContext())
        assertEquals(10, inspectOkVal)

        var inspectErrVal = ""
        ready(Try.err("fail")).asTryFuture().inspectErr { inspectErrVal = it }.poll(TaskContext())
        assertEquals("fail", inspectErrVal)
    }
}
