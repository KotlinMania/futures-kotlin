// port-lint: tests futures-test/src/future/pending_once.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PendingOnceTest {
    @Test
    fun testPendingOnceBehavior() {
        var polled = 0
        val fut = ready(42).pendingOnce()
        val cx = TaskContext(Waker { polled++ })

        val firstPoll = fut.poll(cx)
        assertTrue(firstPoll.isPending())
        assertEquals(1, polled)

        val secondPoll = fut.poll(cx)
        assertTrue(secondPoll.isReady())
        assertEquals(42, secondPoll.unwrap())
    }

    @Test
    fun testInterleavePendingStream() {
        var waked = 0
        val s = streamIter(listOf(1, 2)).interleavePending()
        val cx = TaskContext(Waker { waked++ })

        // 1st poll -> pending
        val p1 = s.pollNext(cx)
        assertTrue(p1.isPending())
        assertEquals(1, waked)

        // 2nd poll -> ready(1)
        val p2 = s.pollNext(cx)
        assertTrue(p2.isReady())
        assertEquals(Yield.value(1), p2.unwrap())

        // 3rd poll -> pending
        val p3 = s.pollNext(cx)
        assertTrue(p3.isPending())
        assertEquals(2, waked)

        // 4th poll -> ready(2)
        val p4 = s.pollNext(cx)
        assertTrue(p4.isReady())
        assertEquals(Yield.value(2), p4.unwrap())
    }
}
