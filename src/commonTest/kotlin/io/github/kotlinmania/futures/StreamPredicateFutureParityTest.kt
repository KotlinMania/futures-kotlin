package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StreamPredicateFutureParityTest {
    private class Input(
        events: List<Poll<Yield<Int?>>>,
    ) : Stream<Int?> {
        private val events = events.toMutableList()
        var polls = 0
            private set

        override fun pollNext(context: TaskContext): Poll<Yield<Int?>> {
            polls += 1
            val event = events.removeAt(0)
            if (event is Poll.Pending) context.wakeByRef()
            return event
        }
    }

    private class Gate(
        private val value: Boolean,
    ) : Future<Boolean> {
        private var released = false
        private var waker: Waker? = null
        var polls = 0
            private set

        fun release() {
            released = true
            waker?.wakeByRef()
        }

        override fun poll(context: TaskContext): Poll<Boolean> {
            polls += 1
            if (!released) {
                waker = context.waker
                return Poll.Pending
            }
            return Poll.Ready(value)
        }
    }

    @Test
    fun allWaitsForPredicateFutureThenShortCircuitsWithoutMoreInput() {
        val input = Input(listOf(Poll.Ready(Yield.Value(null)), Poll.Ready(Yield.Value(7)), Poll.Ready(Yield.End)))
        val gate = Gate(false)
        val calls = mutableListOf<Int?>()
        val future =
            input.all {
                calls.add(it)
                gate
            }
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(listOf<Int?>(null), calls)
        assertEquals(1, input.polls)
        assertFalse(future.isTerminated())
        gate.release()
        assertEquals(false, assertIs<Poll.Ready<Boolean>>(future.poll(TaskContext())).value)
        assertEquals(1, input.polls)
        assertEquals(2, gate.polls)
        assertTrue(future.isTerminated())
        assertEquals("All polled after completion", assertFailsWith<RuntimeException> { future.poll(TaskContext()) }.message)
    }

    @Test
    fun allVisitsNullableItemsInOrderUntilEofWhenPredicatesContinue() {
        val calls = mutableListOf<Int?>()
        val future =
            streamIter(listOf<Int?>(null, 7)).all {
                calls.add(it)
                ready(true)
            }
        assertEquals(true, assertIs<Poll.Ready<Boolean>>(future.poll(TaskContext())).value)
        assertEquals(listOf(null, 7), calls)
        assertTrue(future.isTerminated())
    }

    @Test
    fun allPreservesInputPendingThenItsEmptyStreamIdentity() {
        val input = Input(listOf(Poll.Pending, Poll.Ready(Yield.End)))
        val future = input.all { error("not called") }
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertFalse(future.isTerminated())
        assertEquals(true, assertIs<Poll.Ready<Boolean>>(future.poll(TaskContext())).value)
        assertTrue(future.isTerminated())
    }

    @Test
    fun anyWaitsForPredicateFutureThenShortCircuitsWithoutMoreInput() {
        val input = Input(listOf(Poll.Ready(Yield.Value(null)), Poll.Ready(Yield.Value(7)), Poll.Ready(Yield.End)))
        val gate = Gate(true)
        val calls = mutableListOf<Int?>()
        val future =
            input.any {
                calls.add(it)
                gate
            }
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(listOf<Int?>(null), calls)
        assertEquals(1, input.polls)
        assertFalse(future.isTerminated())
        gate.release()
        assertEquals(true, assertIs<Poll.Ready<Boolean>>(future.poll(TaskContext())).value)
        assertEquals(1, input.polls)
        assertEquals(2, gate.polls)
        assertTrue(future.isTerminated())
        assertEquals("Any polled after completion", assertFailsWith<RuntimeException> { future.poll(TaskContext()) }.message)
    }

    @Test
    fun anyVisitsNullableItemsInOrderUntilEofWhenPredicatesContinue() {
        val calls = mutableListOf<Int?>()
        val future =
            streamIter(listOf<Int?>(null, 7)).any {
                calls.add(it)
                ready(false)
            }
        assertEquals(false, assertIs<Poll.Ready<Boolean>>(future.poll(TaskContext())).value)
        assertEquals(listOf(null, 7), calls)
        assertTrue(future.isTerminated())
    }

    @Test
    fun anyPreservesInputPendingThenItsEmptyStreamIdentity() {
        val input = Input(listOf(Poll.Pending, Poll.Ready(Yield.End)))
        val future = input.any { error("not called") }
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertFalse(future.isTerminated())
        assertEquals(false, assertIs<Poll.Ready<Boolean>>(future.poll(TaskContext())).value)
        assertTrue(future.isTerminated())
    }

    @Test
    fun predicateCreationFailureDoesNotFabricateAllCompletion() {
        val failure = IllegalStateException("predicate")
        val future = streamIter(listOf(1)).all { throw failure }
        assertSame(failure, assertFailsWith<IllegalStateException> { future.poll(TaskContext()) })
        assertFalse(future.isTerminated())
    }

    @Test
    fun returnedPredicateFutureFailureIsNotDiscardedByAny() {
        val failure = IllegalStateException("predicate future")
        val action =
            object : Future<Boolean> {
                override fun poll(context: TaskContext): Poll<Boolean> = throw failure
            }
        val future = streamIter(listOf(1)).any { action }
        assertSame(failure, assertFailsWith<IllegalStateException> { future.poll(TaskContext()) })
        assertFalse(future.isTerminated())
    }
}
