// port-lint: tests futures/tests/future_shared.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.channel.oneshot.channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutureSharedTest {
    @Test
    fun testSharedSingleCaller() {
        val fut = ready(42).shared()
        val cx = TaskContext()

        assertEquals(Poll.ready(42), fut.poll(cx))
        assertTrue(fut.isTerminated())
        assertEquals(42, fut.peek())
        assertEquals(Poll.ready(42), fut.poll(cx))
    }

    @Test
    fun testSharedMultipleCallers() {
        val (tx, rx) = channel<Int>()
        val f1 = rx.shared()
        val f2 = f1.clone()

        val cx = TaskContext()
        assertEquals(Poll.pending(), f1.poll(cx))
        assertEquals(Poll.pending(), f2.poll(cx))

        assertNull(f1.peek())
        assertNull(f2.peek())

        tx.send(42)

        val res1 = f1.poll(cx)
        assertTrue(res1 is Poll.Ready)
        val v1 = (res1.value as Try.Ok).value
        assertEquals(42, v1)

        assertEquals(Try.ok(42), f1.peek())
        assertEquals(Try.ok(42), f2.peek())

        val res2 = f2.poll(cx)
        assertTrue(res2 is Poll.Ready)
        val v2 = (res2.value as Try.Ok).value
        assertEquals(42, v2)
    }

    @Test
    fun testPtrEq() {
        val (_tx, rx) = channel<Int>()
        val shared1 = rx.shared()
        val shared2 = shared1.clone()

        assertTrue(shared1.ptrEq(shared2))
        assertTrue(shared2.ptrEq(shared1))

        val (_tx2, rx2) = channel<Int>()
        val shared3 = rx2.shared()
        assertFalse(shared1.ptrEq(shared3))
    }

    @Test
    fun testPeek() {
        val (tx, rx) = channel<Int>()
        val f1 = rx.shared()
        val f2 = f1.clone()

        for (i in 0 until 2) {
            assertNull(f1.peek())
            assertNull(f2.peek())
        }

        tx.send(42)

        // Peek remains null until polled
        for (i in 0 until 2) {
            assertNull(f1.peek())
            assertNull(f2.peek())
        }

        val cx = TaskContext()
        f1.poll(cx)

        for (i in 0 until 2) {
            assertEquals(Try.ok(42), f2.peek())
        }
    }
}
