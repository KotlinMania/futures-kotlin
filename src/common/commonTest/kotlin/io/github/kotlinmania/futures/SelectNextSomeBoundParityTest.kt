package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SelectNextSomeBoundParityTest {
    private class Input(
        private var done: Boolean,
        events: List<Poll<Yield<Int?>>>,
    ) : FusedStream<Int?> {
        private val events = events.toMutableList()
        var polls = 0

        override fun isTerminated(): Boolean = done

        override fun pollNext(context: TaskContext): Poll<Yield<Int?>> {
            polls++
            check(events.isNotEmpty()) { "unexpected stream poll" }
            val result = events.removeAt(0)
            if (result === Poll.Pending) context.wakeByRef()
            if (result is Poll.Ready && result.value === Yield.End) done = true
            return result
        }
    }

    @Test
    fun terminatedRejectedBeforePoll() {
        val stream = Input(true, emptyList())
        val future = stream.selectNextSome()
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertTrue(future.isTerminated())
        val error = assertFailsWith<IllegalStateException> { future.poll(context) }
        assertEquals("SelectNextSome polled after terminated", error.message)
        assertEquals(0, stream.polls)
        assertEquals(0, wakes)
    }

    @Test
    fun pendingDelegatesWakeup() {
        val stream = Input(false, listOf(Poll.Pending))
        val future = stream.selectNextSome()
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertFalse(future.isTerminated())
        assertSame(Poll.Pending, future.poll(context))
        assertFalse(future.isTerminated())
        assertEquals(1, stream.polls)
        assertEquals(1, wakes)
    }

    @Test
    fun nullableItemThenEnd() {
        val stream = Input(false, listOf(Poll.Ready(Yield.Value(null)), Poll.Ready(Yield.End)))
        val future = stream.selectNextSome()
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertEquals(Poll.Ready(null), future.poll(context))
        assertFalse(future.isTerminated())
        assertSame(Poll.Pending, future.poll(context))
        assertTrue(future.isTerminated())
        val error = assertFailsWith<IllegalStateException> { future.poll(context) }
        assertEquals("SelectNextSome polled after terminated", error.message)
        assertEquals(2, stream.polls)
        assertEquals(1, wakes)
    }

    @Test
    fun nonnullItemThenEnd() {
        val stream = Input(false, listOf(Poll.Ready(Yield.Value(7)), Poll.Ready(Yield.End)))
        val future = stream.selectNextSome()
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertEquals(Poll.Ready(7), future.poll(context))
        assertFalse(future.isTerminated())
        assertSame(Poll.Pending, future.poll(context))
        assertTrue(future.isTerminated())
        assertEquals(2, stream.polls)
        assertEquals(1, wakes)
    }
}
