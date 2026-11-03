package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PeekFutureStateParityTest {
    private sealed interface Step {
        data class Value(
            val value: Int?,
        ) : Step

        data object Pending : Step

        data object End : Step

        data object Panic : Step
    }

    private class Input(
        val events: List<Step>,
    ) : Stream<Int?> {
        var polls = 0

        override fun pollNext(context: TaskContext): Poll<Yield<Int?>> =
            when (val event = events[polls++]) {
                is Step.Value -> Poll.Ready(Yield.Value(event.value))
                Step.Pending -> Poll.Pending
                Step.End -> Poll.Ready(Yield.End)
                Step.Panic -> error("source panic")
            }
    }

    private fun terminated(future: Future<Int?>): Boolean = assertIs<FusedFuture<Int?>>(future).isTerminated()

    @Test
    fun readyValueIsCachedAndFutureIsOneShot() {
        val input = Input(listOf(Step.Value(7)))
        val stream = input.peekable()
        val future = stream.peek()
        assertEquals(Poll.Ready(7), future.poll(TaskContext()))
        val failure = assertFailsWith<IllegalStateException> { future.poll(TaskContext()) }
        assertEquals("Peek polled after completion", failure.message)
        assertEquals(Poll.Ready(Yield.Value(7)), stream.pollNext(TaskContext()))
        assertEquals(1, input.polls)
    }

    @Test
    fun lifecycleIsPerFuture() {
        val input = Input(listOf(Step.Value(7)))
        val stream = input.peekable()
        val future = stream.peek()
        assertFalse(terminated(future))
        assertEquals(Poll.Ready(7), future.poll(TaskContext()))
        assertTrue(terminated(future))
        assertFalse(stream.isTerminated())
        val second = stream.peek()
        assertFalse(terminated(second))
        assertEquals(Poll.Ready(7), second.poll(TaskContext()))
        assertTrue(terminated(second))
        assertEquals(Poll.Ready(Yield.Value(7)), stream.pollNext(TaskContext()))
        assertEquals(1, input.polls)
    }

    @Test
    fun pendingRetainsReceiver() {
        val input = Input(listOf(Step.Pending, Step.Value(9)))
        val stream = input.peekable()
        val future = stream.peek()
        assertFalse(terminated(future))
        assertEquals(Poll.Pending, future.poll(TaskContext()))
        assertFalse(terminated(future))
        assertEquals(1, input.polls)
        assertEquals(Poll.Ready(9), future.poll(TaskContext()))
        assertTrue(terminated(future))
        assertEquals(Poll.Ready(Yield.Value(9)), stream.pollNext(TaskContext()))
        assertEquals(2, input.polls)
    }

    @Test
    fun eofCompletesAndFusesSource() {
        val input = Input(listOf(Step.End, Step.Value(99)))
        val stream = input.peekable()
        val future = stream.peek()
        assertFalse(terminated(future))
        assertEquals(Poll.Ready<Int?>(null), future.poll(TaskContext()))
        assertTrue(terminated(future))
        assertTrue(stream.isTerminated())
        val second = stream.peek()
        assertFalse(terminated(second))
        assertEquals(Poll.Ready<Int?>(null), second.poll(TaskContext()))
        assertTrue(terminated(second))
        assertEquals(Poll.Ready(Yield.End), stream.pollNext(TaskContext()))
        assertEquals(1, input.polls)
    }

    @Test
    fun nullableItemRemainsQueued() {
        val input = Input(listOf(Step.Value(null), Step.End))
        val stream = input.peekable()
        val future = stream.peek()
        assertEquals(Poll.Ready<Int?>(null), future.poll(TaskContext()))
        assertTrue(terminated(future))
        assertFalse(stream.isTerminated())
        assertEquals(Poll.Ready(Yield.Value<Int?>(null)), stream.pollNext(TaskContext()))
        assertEquals(1, input.polls)
        assertEquals(Poll.Ready(Yield.End), stream.pollNext(TaskContext()))
        assertEquals(2, input.polls)
    }

    @Test
    fun sourcePanicDoesNotTakeReceiver() {
        val input = Input(listOf(Step.Panic, Step.Value(8)))
        val stream = input.peekable()
        val future = stream.peek()
        val failure = assertFailsWith<IllegalStateException> { future.poll(TaskContext()) }
        assertEquals("source panic", failure.message)
        assertFalse(terminated(future))
        assertEquals(1, input.polls)
        assertEquals(Poll.Ready(8), future.poll(TaskContext()))
        assertTrue(terminated(future))
        assertEquals(Poll.Ready(Yield.Value(8)), stream.pollNext(TaskContext()))
        assertEquals(2, input.polls)
    }

    @Test
    fun cancellingPendingAllowsNewPeek() {
        val input = Input(listOf(Step.Pending, Step.Value(6)))
        val stream = input.peekable()
        val future = stream.peek()
        assertEquals(Poll.Pending, future.poll(TaskContext()))
        val second = stream.peek()
        assertEquals(Poll.Ready(6), second.poll(TaskContext()))
        assertEquals(Poll.Ready(Yield.Value(6)), stream.pollNext(TaskContext()))
        assertEquals(2, input.polls)
    }

    @Test
    fun alreadyCachedItemNeedsNoNewSourcePoll() {
        val input = Input(listOf(Step.Value(5)))
        val stream = input.peekable()
        assertEquals(Poll.Ready(5), stream.pollPeek(TaskContext()))
        val future = stream.peek()
        assertFalse(terminated(future))
        assertEquals(Poll.Ready(5), future.poll(TaskContext()))
        assertTrue(terminated(future))
        assertEquals(Poll.Ready(Yield.Value(5)), stream.pollNext(TaskContext()))
        assertEquals(1, input.polls)
    }
}
