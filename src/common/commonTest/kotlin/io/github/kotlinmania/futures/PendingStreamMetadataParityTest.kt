package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingStreamMetadataParityTest {
    @Test
    fun inactiveBeforeAndAfterPoll() {
        val stream = pendingStream<Int?>()
        assertTrue(stream.isTerminated())
        repeat(8) {
            assertEquals(Poll.Pending, stream.pollNext(TaskContext()))
            assertTrue(stream.isTerminated())
        }
    }

    @Test
    fun exactZeroHintBeforeAndAfterPoll() {
        val stream = pendingStream<Int>()
        assertEquals(SizeHint(0, 0), stream.sizeHint())
        repeat(8) {
            assertEquals(Poll.Pending, stream.pollNext(TaskContext()))
            assertEquals(SizeHint(0, 0), stream.sizeHint())
        }
    }

    @Test
    fun pendingPollDoesNotWakeOrYieldEnd() {
        val stream = pendingStream<Int?>()
        var wakes = 0
        val context = TaskContext(Waker { wakes += 1 })
        repeat(8) { assertEquals(Poll.Pending, stream.pollNext(context)) }
        assertEquals(0, wakes)
    }

    @Test
    fun positiveTakeKeepsInactiveMetadata() {
        val stream = pendingStream<Int>().take(5)
        assertTrue(stream.isTerminated())
        assertEquals(SizeHint(0, 0), stream.sizeHint())
        assertEquals(Poll.Pending, stream.pollNext(TaskContext()))
        assertTrue(stream.isTerminated())
        assertEquals(SizeHint(0, 0), stream.sizeHint())
    }

    @Test
    fun fuseDoesNotSynthesizeAnEnd() {
        val stream = pendingStream<Int>().fuse()
        assertFalse(stream.isTerminated())
        assertEquals(SizeHint(0, 0), stream.sizeHint())
        assertEquals(Poll.Pending, stream.pollNext(TaskContext()))
        assertFalse(stream.isTerminated())
        assertEquals(SizeHint(0, 0), stream.sizeHint())
    }

    @Test
    fun selectNextSomeDetectsInactiveStream() {
        val stream = pendingStream<Int>()
        val future = stream.selectNextSome()
        assertTrue(future.isTerminated())
        val failure = assertFailsWith<IllegalStateException> { future.poll(TaskContext()) }
        assertEquals("SelectNextSome polled after terminated", failure.message)
    }
}
