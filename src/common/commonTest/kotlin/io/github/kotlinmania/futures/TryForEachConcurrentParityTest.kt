package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TryForEachConcurrentParityTest {
    private class Input(
        events: List<Poll<Yield<Try<Int, String>>>>,
        private val log: MutableList<String>? = null,
    ) : TryStream<Int, String> {
        private val events = events.toMutableList()
        var polls = 0
            private set

        override fun pollNext(context: TaskContext): Poll<Yield<Try<Int, String>>> {
            polls += 1
            log?.add("input$polls")
            return if (events.isEmpty()) Poll.Ready(Yield.End) else events.removeAt(0)
        }
    }

    private class Gate : Future<Try<Unit, String>> {
        private var released = false
        private var waker: Waker? = null
        var polls = 0
            private set

        fun release() {
            released = true
            waker?.wakeByRef()
        }

        override fun poll(context: TaskContext): Poll<Try<Unit, String>> {
            polls += 1
            if (!released) {
                waker = context.waker
                return Poll.Pending
            }
            return Poll.Ready(Try.Ok(Unit))
        }
    }

    private fun ok(item: Int): Poll<Yield<Try<Int, String>>> = Poll.Ready(Yield.Value(Try.Ok(item)))

    @Test
    fun zeroLimitIsUnlimited() {
        val input = Input(listOf(ok(1), ok(2), Poll.Ready(Yield.End)))
        val first = Gate()
        val second = Gate()
        val calls = mutableListOf<Int>()
        val future =
            input.tryForEachConcurrent(limit = 0) {
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
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(TaskContext()))
        assertTrue(future.isTerminated())
    }

    @Test
    fun sleepingActionsAreNotRepolledWithoutTheirWake() {
        val input = Input(listOf(ok(1), ok(2), Poll.Ready(Yield.End)))
        val first = Gate()
        val second = Gate()
        val future = input.tryForEachConcurrent(limit = 2) { if (it == 1) first else second }
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
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(TaskContext()))
        assertTrue(future.isTerminated())
    }

    @Test
    fun oneSlotBackpressuresInputUntilItsActionCompletes() {
        val input = Input(listOf(ok(1), ok(2), Poll.Ready(Yield.End)))
        val first = Gate()
        val calls = mutableListOf<Int>()
        val future =
            input.tryForEachConcurrent(limit = 1) {
                calls.add(it)
                if (it == 1) first else ready<Try<Unit, String>>(Try.Ok(Unit))
            }
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(listOf(1), calls)
        assertEquals(1, input.polls)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(1, input.polls)
        assertEquals(1, first.polls)
        first.release()
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(TaskContext()))
        assertEquals(listOf(1, 2), calls)
        assertEquals(3, input.polls)
    }

    @Test
    fun inputErrorReplacesTheQueueAndDoesNotPollCancelledActions() {
        val input = Input(listOf(ok(1), Poll.Ready(Yield.Value(Try.Err("input"))), ok(2)))
        val first = Gate()
        val future = input.tryForEachConcurrent { first }
        assertEquals(Poll.Ready(Try.Err("input")), future.poll(TaskContext()))
        assertEquals(2, input.polls)
        assertEquals(1, first.polls)
        assertTrue(future.isTerminated())
        first.release()
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(TaskContext()))
        assertEquals(2, input.polls)
        assertEquals(1, first.polls)
    }

    @Test
    fun actionErrorDoesNotRescanSleepingActionsAndStopsInput() {
        val input = Input(listOf(ok(1), ok(2), ok(3)))
        val first = Gate()
        val future =
            input.tryForEachConcurrent(limit = 2) {
                if (it == 1) first else ready<Try<Unit, String>>(Try.Err("action"))
            }
        assertEquals(Poll.Ready(Try.Err("action")), future.poll(TaskContext()))
        assertEquals(2, input.polls)
        assertEquals(1, first.polls)
        assertTrue(future.isTerminated())
        first.release()
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(TaskContext()))
        assertEquals(1, first.polls)
    }

    @Test
    fun readyActionsInterleaveOneInputPollWithOneQueueResult() {
        val log = mutableListOf<String>()
        val input = Input(listOf(ok(1), ok(2), Poll.Ready(Yield.End)), log)
        val future =
            input.tryForEachConcurrent {
                Future<Try<Unit, String>> { _ ->
                    log.add("action$it")
                    Poll.Ready(Try.Ok(Unit))
                }
            }
        assertFalse(future.isTerminated())
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(TaskContext()))
        assertEquals(listOf("input1", "action1", "input2", "action2", "input3"), log)
        assertTrue(future.isTerminated())
    }
}
