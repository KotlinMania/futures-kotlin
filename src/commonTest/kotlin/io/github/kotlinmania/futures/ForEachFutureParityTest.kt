package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ForEachFutureParityTest {
    private class Input(
        events: List<Poll<Yield<Int?>>>,
    ) : FusedStream<Int?> {
        private val events = events.toMutableList()
        var polls = 0
            private set

        override fun isTerminated(): Boolean = events.isEmpty()

        override fun pollNext(context: TaskContext): Poll<Yield<Int?>> {
            polls += 1
            val result = if (events.isEmpty()) Poll.Ready(Yield.End) else events.removeAt(0)
            if (result is Poll.Pending) context.wakeByRef()
            return result
        }
    }

    private class Gate : Future<Unit> {
        private var released = false
        private var waker: Waker? = null
        var polls = 0
            private set

        fun release() {
            released = true
            waker?.wakeByRef()
        }

        override fun poll(context: TaskContext): Poll<Unit> {
            polls += 1
            if (!released) {
                waker = context.waker
                return Poll.Pending
            }
            return Poll.Ready(Unit)
        }
    }

    @Test
    fun pendingCallbackFutureBlocksFurtherInputAndIsNotRecreated() {
        val input = Input(listOf(Poll.Ready(Yield.Value(1)), Poll.Ready(Yield.Value(2)), Poll.Ready(Yield.End)))
        val gate = Gate()
        val calls = mutableListOf<Int?>()
        val future =
            input.forEach {
                calls.add(it)
                if (it == 1) gate else ready(Unit)
            }
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(listOf<Int?>(1), calls)
        assertEquals(1, input.polls)
        assertEquals(1, gate.polls)
        assertFalse(future.isTerminated())
        gate.release()
        assertIs<Poll.Ready<Unit>>(future.poll(TaskContext()))
        assertEquals(listOf<Int?>(1, 2), calls)
        assertEquals(2, gate.polls)
        assertEquals(3, input.polls)
        assertTrue(future.isTerminated())
    }

    @Test
    fun returnedFutureFailureIsPropagatedRatherThanDiscarded() {
        val failure = IllegalStateException("action future")
        val action =
            object : Future<Unit> {
                override fun poll(context: TaskContext): Poll<Unit> = throw failure
            }
        val future = streamIter(listOf(1)).forEach { action }
        assertSame(failure, assertFailsWith<IllegalStateException> { future.poll(TaskContext()) })
    }

    @Test
    fun fusedEmptyInputIsTerminatedBeforePolling() {
        val input = Input(emptyList())
        val future = input.forEach { ready(Unit) }
        assertTrue(future.isTerminated())
        assertEquals(0, input.polls)
    }

    @Test
    fun outerPendingAndThenInnerPendingAreSeparateStates() {
        val input = Input(listOf(Poll.Pending, Poll.Ready(Yield.Value(3)), Poll.Ready(Yield.End)))
        val gate = Gate()
        var calls = 0
        val future =
            input.forEach {
                calls += 1
                gate
            }
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(0, calls)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(1, calls)
        assertEquals(2, input.polls)
        gate.release()
        assertIs<Poll.Ready<Unit>>(future.poll(TaskContext()))
        assertEquals(1, calls)
    }

    @Test
    fun nullableItemsReachImmediateFutureCallbacksInOrder() {
        val calls = mutableListOf<Int?>()
        val future =
            streamIter(listOf<Int?>(null, 7)).forEach {
                calls.add(it)
                ready(Unit)
            }
        assertIs<Poll.Ready<Unit>>(future.poll(TaskContext()))
        assertEquals(listOf(null, 7), calls)
    }

    @Test
    fun pendingThenEndDoesNotCreateAnActionFuture() {
        val input = Input(listOf(Poll.Pending, Poll.Ready(Yield.End)))
        var calls = 0
        val future =
            input.forEach {
                calls += 1
                ready(Unit)
            }
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertIs<Poll.Ready<Unit>>(future.poll(TaskContext()))
        assertEquals(0, calls)
    }
}
