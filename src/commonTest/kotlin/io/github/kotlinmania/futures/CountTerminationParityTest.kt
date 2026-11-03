package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CountTerminationParityTest {
    private sealed interface Step {
        data class Value(
            val value: Int?,
        ) : Step

        data object Pending : Step

        data object InactivePending : Step

        data object End : Step
    }

    private class Input(
        val events: List<Step>,
    ) : FusedStream<Int?> {
        var terminated = false
        var polls = 0

        override fun isTerminated(): Boolean = terminated

        override fun pollNext(context: TaskContext): Poll<Yield<Int?>> =
            when (val event = events.getOrNull(polls++) ?: Step.Pending) {
                is Step.Value -> {
                    Poll.Ready(Yield.Value(event.value))
                }

                Step.Pending -> {
                    Poll.Pending
                }

                Step.InactivePending -> {
                    terminated = true
                    Poll.Pending
                }

                Step.End -> {
                    terminated = true
                    Poll.Ready(Yield.End)
                }
            }
    }

    @Test
    fun emptySourceIsTerminatedBeforeCountPoll() {
        val future = emptyStream<Int>().count()
        assertTrue(future.isTerminated())
        assertEquals(Poll.Ready(0), future.poll(TaskContext()))
        assertTrue(future.isTerminated())
    }

    @Test
    fun inactivePendingSourceStaysPending() {
        val future = pendingStream<Int>().count()
        assertTrue(future.isTerminated())
        assertEquals(Poll.Pending, future.poll(TaskContext()))
        assertTrue(future.isTerminated())
    }

    @Test
    fun externalTerminationChangeNeedsNoPoll() {
        val input = Input(emptyList())
        val future = input.count()
        assertFalse(future.isTerminated())
        assertEquals(0, input.polls)
        input.terminated = true
        assertTrue(future.isTerminated())
        assertEquals(0, input.polls)
    }

    @Test
    fun sourceCanBecomeInactiveWhileCountIsPending() {
        val input = Input(listOf(Step.Value(1), Step.InactivePending))
        val future = input.count()
        assertFalse(future.isTerminated())
        assertEquals(Poll.Pending, future.poll(TaskContext()))
        assertTrue(future.isTerminated())
        assertEquals(2, input.polls)
    }

    @Test
    fun pendingPreservesNullableItemCount() {
        val input = Input(listOf(Step.Value(1), Step.Pending, Step.Value(null), Step.End))
        val future = input.count()
        assertFalse(future.isTerminated())
        assertEquals(Poll.Pending, future.poll(TaskContext()))
        assertFalse(future.isTerminated())
        assertEquals(2, input.polls)
        assertEquals(Poll.Ready(2), future.poll(TaskContext()))
        assertTrue(future.isTerminated())
        assertEquals(4, input.polls)
    }
}
