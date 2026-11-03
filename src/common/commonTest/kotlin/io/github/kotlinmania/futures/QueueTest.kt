// port-lint: tests futures-channel/src/mpsc/queue.rs
@file:OptIn(
    kotlin.experimental.ExperimentalObjCRefinement::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
)

package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.channel.mpsc.PopResult
import io.github.kotlinmania.futures.channel.mpsc.Queue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueueTest {
    @Test
    fun testQueuePushPop() {
        val q = Queue<Int>()
        assertTrue(q.pop() is PopResult.Empty)

        q.push(1)
        q.push(2)
        q.push(3)

        val r1 = q.pop()
        assertTrue(r1 is PopResult.Data)
        assertEquals(1, r1.value)

        val r2 = q.pop()
        assertTrue(r2 is PopResult.Data)
        assertEquals(2, r2.value)

        val r3 = q.pop()
        assertTrue(r3 is PopResult.Data)
        assertEquals(3, r3.value)

        assertTrue(q.pop() is PopResult.Empty)
    }

    @Test
    fun testQueuePopSpin() {
        val q = Queue<String>()
        assertEquals(null, q.popSpin())

        q.push("a")
        q.push("b")

        assertEquals("a", q.popSpin())
        assertEquals("b", q.popSpin())
        assertEquals(null, q.popSpin())
    }
}
