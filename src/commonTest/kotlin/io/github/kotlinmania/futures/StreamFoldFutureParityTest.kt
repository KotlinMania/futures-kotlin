package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StreamFoldFutureParityTest {
    private class Input(
        events: List<Poll<Yield<Int>>>,
    ) : Stream<Int> {
        private val events = events.toMutableList()
        var polls = 0
            private set

        override fun pollNext(context: TaskContext): Poll<Yield<Int>> {
            polls += 1
            val result = events.removeAt(0)
            if (result is Poll.Pending) context.wakeByRef()
            return result
        }
    }

    private class Gate : Future<Int> {
        private var released = false
        private var waker: Waker? = null
        var polls = 0
            private set

        fun release() {
            released = true
            waker?.wakeByRef()
        }

        override fun poll(context: TaskContext): Poll<Int> {
            polls += 1
            if (!released) {
                waker = context.waker
                return Poll.Pending
            }
            return Poll.Ready(10)
        }
    }

    @Test
    fun pendingOperationFutureBlocksInputAndFeedsItsResultIntoTheNextStep() {
        val input = Input(listOf(Poll.Ready(Yield.Value(1)), Poll.Ready(Yield.Value(2)), Poll.Ready(Yield.End)))
        val gate = Gate()
        val calls = mutableListOf<Pair<Int, Int>>()
        val fold =
            input.fold<Int, Int>(0) { accum, item ->
                calls.add(Pair(accum, item))
                if (item == 1) gate else ready(accum + item)
            }
        assertSame(Poll.Pending, fold.poll(TaskContext()))
        assertEquals(listOf(Pair(0, 1)), calls)
        assertEquals(1, input.polls)
        assertFalse(fold.isTerminated())
        gate.release()
        assertEquals(12, assertIs<Poll.Ready<Int>>(fold.poll(TaskContext())).value)
        assertEquals(listOf(Pair(0, 1), Pair(10, 2)), calls)
        assertEquals(2, gate.polls)
        assertTrue(fold.isTerminated())
    }

    @Test
    fun nullableAccumulatorAndNullableFutureOutputRemainPresent() {
        val seen = mutableListOf<Int?>()
        val fold =
            streamIter(listOf(1, 2)).fold<Int, Int?>(null) { accum, item ->
                seen.add(accum)
                ready(if (item == 1) null else 7)
            }
        assertEquals(7, assertIs<Poll.Ready<Int?>>(fold.poll(TaskContext())).value)
        assertEquals(listOf<Int?>(null, null), seen)
        assertTrue(fold.isTerminated())
    }

    @Test
    fun operationCreationFailureLeavesTheAccumulatorTakenAndFoldTerminated() {
        val failure = IllegalStateException("operation")
        var calls = 0
        val fold =
            streamIter(listOf(1, 2)).fold<Int, Int>(3) { _, _ ->
                calls += 1
                throw failure
            }
        assertSame(failure, assertFailsWith<IllegalStateException> { fold.poll(TaskContext()) })
        assertTrue(fold.isTerminated())
        assertEquals("Fold polled after completion", assertFailsWith<RuntimeException> { fold.poll(TaskContext()) }.message)
        assertEquals(1, calls)
    }

    @Test
    fun returnedFutureFailurePropagatesWithThePendingFutureStillStored() {
        val failure = IllegalStateException("operation future")
        val action =
            object : Future<Int> {
                override fun poll(context: TaskContext): Poll<Int> = throw failure
            }
        val fold = streamIter(listOf(1)).fold<Int, Int>(3) { _, _ -> action }
        assertSame(failure, assertFailsWith<IllegalStateException> { fold.poll(TaskContext()) })
        assertFalse(fold.isTerminated())
    }

    @Test
    fun outerPendingDoesNotTakeTheAccumulatorOrInvokeTheOperation() {
        val input = Input(listOf(Poll.Pending, Poll.Ready(Yield.Value(2)), Poll.Ready(Yield.End)))
        var calls = 0
        val fold =
            input.fold<Int, Int>(5) { accum, item ->
                calls += 1
                ready(accum + item)
            }
        assertSame(Poll.Pending, fold.poll(TaskContext()))
        assertEquals(0, calls)
        assertEquals(7, assertIs<Poll.Ready<Int>>(fold.poll(TaskContext())).value)
        assertEquals(1, calls)
    }

    @Test
    fun emptyStreamReturnsAnInitialNullAndThenRejectsFurtherPolls() {
        val fold = streamIter(emptyList<Int>()).fold<Int, Int?>(null) { _, _ -> error("not called") }
        assertFalse(fold.isTerminated())
        assertNull(assertIs<Poll.Ready<Int?>>(fold.poll(TaskContext())).value)
        assertTrue(fold.isTerminated())
        assertEquals("Fold polled after completion", assertFailsWith<RuntimeException> { fold.poll(TaskContext()) }.message)
    }
}
