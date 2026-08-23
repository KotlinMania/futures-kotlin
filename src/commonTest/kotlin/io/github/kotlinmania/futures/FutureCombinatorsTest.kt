// port-lint: source futures-util/src/future/mod.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
