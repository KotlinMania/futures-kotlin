package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatelessCloneParityTest {
    private class NoClone

    @Test
    fun emptyStreamCloneHasNoPayloadBound() {
        val original = emptyStream<NoClone?>()
        val cloned: EmptyStream<NoClone?> = original.clone()
        var wakes = 0
        val context = TaskContext(Waker { wakes += 1 })
        assertTrue(original.isTerminated())
        assertTrue(cloned.isTerminated())
        repeat(8) {
            assertEquals(Poll.Ready(Yield.End), original.pollNext(context))
            assertEquals(Poll.Ready(Yield.End), cloned.pollNext(context))
            assertEquals(SizeHint(0, 0), original.sizeHint())
            assertEquals(SizeHint(0, 0), cloned.sizeHint())
            assertTrue(original.isTerminated())
            assertTrue(cloned.isTerminated())
        }
        val after = original.clone()
        assertEquals(Poll.Ready(Yield.End), after.pollNext(context))
        assertTrue(after.isTerminated())
        assertEquals(SizeHint(0, 0), after.sizeHint())
        assertEquals(0, wakes)
    }

    @Test
    fun pendingStreamCloneHasNoPayloadBound() {
        val original = pendingStream<NoClone?>()
        val cloned: PendingStream<NoClone?> = original.clone()
        var wakes = 0
        val context = TaskContext(Waker { wakes += 1 })
        assertTrue(original.isTerminated())
        assertTrue(cloned.isTerminated())
        repeat(8) {
            assertEquals(Poll.Pending, original.pollNext(context))
            assertEquals(Poll.Pending, cloned.pollNext(context))
            assertEquals(SizeHint(0, 0), original.sizeHint())
            assertEquals(SizeHint(0, 0), cloned.sizeHint())
            assertTrue(original.isTerminated())
            assertTrue(cloned.isTerminated())
        }
        val after = original.clone()
        assertEquals(Poll.Pending, after.pollNext(context))
        assertTrue(after.isTerminated())
        assertEquals(SizeHint(0, 0), after.sizeHint())
        assertEquals(0, wakes)
    }

    @Test
    fun pendingFutureCloneHasNoOutputBound() {
        val original = pending<NoClone?>()
        val cloned: Pending<NoClone?> = original.clone()
        var wakes = 0
        val context = TaskContext(Waker { wakes += 1 })
        assertTrue(original.isTerminated())
        assertTrue(cloned.isTerminated())
        repeat(8) {
            assertEquals(Poll.Pending, original.poll(context))
            assertEquals(Poll.Pending, cloned.poll(context))
            assertTrue(original.isTerminated())
            assertTrue(cloned.isTerminated())
        }
        val after = original.clone()
        assertEquals(Poll.Pending, after.poll(context))
        assertTrue(after.isTerminated())
        assertEquals(0, wakes)
    }
}
