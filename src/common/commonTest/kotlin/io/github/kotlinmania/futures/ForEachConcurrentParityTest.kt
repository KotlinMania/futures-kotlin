package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ForEachConcurrentParityTest {
    private class Input(
        events: List<Poll<Yield<Int>>>,
        private val log: MutableList<String>? = null,
    ) : Stream<Int> {
        private val events = events.toMutableList()
        var polls = 0
            private set

        override fun pollNext(context: TaskContext): Poll<Yield<Int>> {
            polls += 1
            log?.add("input$polls")
            return if (events.isEmpty()) Poll.Ready(Yield.End) else events.removeAt(0)
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

    private fun ok(item: Int): Poll<Yield<Int>> = Poll.Ready(Yield.Value(item))

    @Test
    fun zeroLimitIsUnlimited() {
        val input = Input(listOf(ok(1), ok(2), Poll.Ready(Yield.End)))
        val first = Gate()
        val second = Gate()
        val calls = mutableListOf<Int>()
        val future =
            input.forEachConcurrent(limit = 0) {
                calls.add(it)
                if (it == 1) first else second
            }
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(listOf(1, 2), calls)
        assertEquals(3, input.polls)
        assertEquals(1, first.polls)
        assertEquals(1, second.polls)
        first.release()
        second.release()
        assertEquals(Poll.Ready(Unit), future.poll(TaskContext()))
        assertTrue(future.isTerminated())
    }

    @Test
    fun sleepingActionsAreNotRepolledWithoutTheirWake() {
        val input = Input(listOf(ok(1), ok(2), Poll.Ready(Yield.End)))
        val first = Gate()
        val second = Gate()
        val future = input.forEachConcurrent(limit = 2) { if (it == 1) first else second }
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(2, input.polls)
        assertEquals(1, first.polls)
        assertEquals(1, second.polls)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(1, first.polls)
        assertEquals(1, second.polls)
        second.release()
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(3, input.polls)
        assertEquals(1, first.polls)
        assertEquals(2, second.polls)
        assertFalse(future.isTerminated())
        first.release()
        assertEquals(Poll.Ready(Unit), future.poll(TaskContext()))
        assertTrue(future.isTerminated())
    }

    @Test
    fun oneSlotBackpressuresInputUntilItsActionCompletes() {
        val input = Input(listOf(ok(1), ok(2), Poll.Ready(Yield.End)))
        val first = Gate()
        val calls = mutableListOf<Int>()
        val future =
            input.forEachConcurrent(limit = 1) {
                calls.add(it)
                if (it == 1) first else ready(Unit)
            }
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(listOf(1), calls)
        assertEquals(1, input.polls)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(1, input.polls)
        assertEquals(1, first.polls)
        first.release()
        assertEquals(Poll.Ready(Unit), future.poll(TaskContext()))
        assertEquals(listOf(1, 2), calls)
        assertEquals(3, input.polls)
    }

    @Test
    fun readyActionsInterleaveOneInputPollWithOneQueueResult() {
        val log = mutableListOf<String>()
        val input = Input(listOf(ok(1), ok(2), Poll.Ready(Yield.End)), log)
        val future =
            input.forEachConcurrent(null) {
                Future<Unit> { _ ->
                    log.add("action$it")
                    Poll.Ready(Unit)
                }
            }
        assertFalse(future.isTerminated())
        assertEquals(Poll.Ready(Unit), future.poll(TaskContext()))
        assertEquals(listOf("input1", "action1", "input2", "action2", "input3"), log)
        assertTrue(future.isTerminated())
    }

    @Test
    fun callbackPanicHappensAfterEarlierActionHasBeenPolled() {
        val input = Input(listOf(ok(1), ok(2), ok(3)))
        val first = Gate()
        val failure = IllegalStateException("callback")
        val calls = mutableListOf<Int>()
        val future =
            input.forEachConcurrent(2) {
                calls.add(it)
                if (it == 2) throw failure
                first
            }
        assertSame(failure, assertFailsWith<IllegalStateException> { future.poll(TaskContext()) })
        assertEquals(listOf(1, 2), calls)
        assertEquals(2, input.polls)
        assertEquals(1, first.polls)
        assertFalse(future.isTerminated())
    }

    @Test
    fun pendingInputDoesNotCauseExtraActionPollsOrExtraInputReads() {
        val input = Input(listOf(ok(1), Poll.Pending, Poll.Pending, Poll.Ready(Yield.End)))
        val first = Gate()
        val future = input.forEachConcurrent(null) { first }
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(2, input.polls)
        assertEquals(1, first.polls)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(3, input.polls)
        assertEquals(1, first.polls)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(4, input.polls)
        assertEquals(1, first.polls)
        first.release()
        assertEquals(Poll.Ready(Unit), future.poll(TaskContext()))
        assertEquals(4, input.polls)
        assertTrue(future.isTerminated())
    }
}
