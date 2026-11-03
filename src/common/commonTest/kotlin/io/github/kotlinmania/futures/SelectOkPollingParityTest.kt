package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class SelectOkPollingParityTest {
    private class StepFuture(
        val id: Int,
        private var pendingPolls: Int,
        private val result: Try<Int?, String?>,
        private val trace: MutableList<Int>,
    ) : Future<Try<Int?, String?>> {
        var polls: Int = 0
            private set
        private var completed = false

        override fun poll(context: TaskContext): Poll<Try<Int?, String?>> {
            check(!completed) { "fixture polled after completion" }
            trace.add(id)
            polls += 1
            if (pendingPolls > 0) {
                pendingPolls -= 1
                context.wakeByRef()
                return Poll.Pending
            }
            completed = true
            return Poll.Ready(result)
        }
    }

    @Test
    fun errorRestartsTheScanBeforeALaterSuccess() {
        val trace = mutableListOf<Int>()
        val early = StepFuture(0, 1, Try.Ok(7), trace)
        val failure = StepFuture(1, 0, Try.Err("discard"), trace)
        val late = StepFuture(2, 0, Try.Ok(99), trace)
        val future = selectOk(listOf(early, failure, late))
        val result = assertIs<Poll.Ready<Try<Pair<Int?, List<Future<Try<Int?, String?>>>>, String?>>>(future.poll(TaskContext()))
        val success = assertIs<Try.Ok<Pair<Int?, List<Future<Try<Int?, String?>>>>>>(result.value).value
        assertEquals(7, success.first)
        assertEquals(listOf(0, 1, 0), trace)
        assertSame(late, success.second.single())
        assertEquals(0, late.polls)
    }

    @Test
    fun pendingEarlierFutureIsRepolledAfterAnError() {
        val trace = mutableListOf<Int>()
        val early = StepFuture(0, 2, Try.Ok(7), trace)
        val failure = StepFuture(1, 0, Try.Err("discard"), trace)
        val late = StepFuture(2, 1, Try.Ok(99), trace)
        val future = selectOk(listOf(early, failure, late))
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(listOf(0, 1, 0, 2), trace)
        val result = assertIs<Poll.Ready<Try<Pair<Int?, List<Future<Try<Int?, String?>>>>, String?>>>(future.poll(TaskContext()))
        val success = assertIs<Try.Ok<Pair<Int?, List<Future<Try<Int?, String?>>>>>>(result.value).value
        assertEquals(7, success.first)
        assertSame(late, success.second.single())
    }

    @Test
    fun completedCombinatorDoesNotPollItsReturnedFuturesAgain() {
        val trace = mutableListOf<Int>()
        val winner = StepFuture(0, 0, Try.Ok(7), trace)
        val remaining = StepFuture(1, 1, Try.Ok(99), trace)
        val future = selectOk(listOf(winner, remaining))
        assertIs<Poll.Ready<*>>(future.poll(TaskContext()))
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(listOf(0), trace)
        assertEquals(0, remaining.polls)
    }

    @Test
    fun nullableSuccessAndLastErrorPayloadsRemainTagged() {
        val success = selectOk(listOf(err<Int?, String?>(null), ok<Int?, String?>(null))).poll(TaskContext())
        val readySuccess = assertIs<Poll.Ready<Try<Pair<Int?, List<Future<Try<Int?, String?>>>>, String?>>>(success)
        val value = assertIs<Try.Ok<Pair<Int?, List<Future<Try<Int?, String?>>>>>>(readySuccess.value).value
        assertNull(value.first)
        assertEquals(emptyList(), value.second)
        val failure = selectOk(listOf(err<Int?, String?>("first"), err<Int?, String?>(null))).poll(TaskContext())
        val readyFailure = assertIs<Poll.Ready<Try<Pair<Int?, List<Future<Try<Int?, String?>>>>, String?>>>(failure)
        assertNull(assertIs<Try.Err<String?>>(readyFailure.value).error)
    }

    @Test
    fun entirelyPendingScanPollsEachFutureOnce() {
        val trace = mutableListOf<Int>()
        val future = selectOk(listOf(StepFuture(0, 1, Try.Ok(7), trace), StepFuture(1, 1, Try.Ok(99), trace)))
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(listOf(0, 1), trace)
    }
}
