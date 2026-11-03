package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SelectAllPollingParityTest {
    private class StepFuture(
        val id: Int,
        private val value: Int?,
        private val isReady: Boolean,
        private val trace: MutableList<Int>,
    ) : Future<Int?> {
        override fun poll(context: TaskContext): Poll<Int?> {
            trace.add(id)
            if (!isReady) {
                context.wakeByRef()
                return Poll.Pending
            }
            return Poll.Ready(value)
        }
    }

    @Test
    fun firstWinnerUsesUpstreamSwapRemoval() {
        val trace = mutableListOf<Int>()
        val futures = (0..3).map { StepFuture(it, it, true, trace) }
        val result = assertIs<Poll.Ready<Triple<Int?, Int, List<Future<Int?>>>>>(selectAll(futures).poll(TaskContext())).value
        assertEquals(0, result.first)
        assertEquals(0, result.second)
        assertEquals(listOf(0), trace)
        assertEquals(listOf(3, 1, 2), result.third.map { (it as StepFuture).id })
        assertSame(futures[3], result.third[0])
    }

    @Test
    fun middleWinnerPreservesPollOrderAndSwapsLastIntoItsSlot() {
        val trace = mutableListOf<Int>()
        val futures = (0..3).map { StepFuture(it, it, it == 1, trace) }
        val result = assertIs<Poll.Ready<Triple<Int?, Int, List<Future<Int?>>>>>(selectAll(futures).poll(TaskContext())).value
        assertEquals(1, result.first)
        assertEquals(1, result.second)
        assertEquals(listOf(0, 1), trace)
        assertEquals(listOf(0, 3, 2), result.third.map { (it as StepFuture).id })
        assertSame(futures[3], result.third[1])
    }

    @Test
    fun successTransfersAllRemainingFuturesOutOfTheCombinator() {
        val trace = mutableListOf<Int>()
        val waiting = StepFuture(0, 9, false, trace)
        val winner = StepFuture(1, 7, true, trace)
        val future = selectAll(listOf(waiting, winner))
        val result = assertIs<Poll.Ready<Triple<Int?, Int, List<Future<Int?>>>>>(future.poll(TaskContext())).value
        assertEquals(7, result.first)
        assertEquals(1, result.second)
        assertSame(waiting, result.third.single())
        assertTrue(future.intoInner().isEmpty())
    }

    @Test
    fun nullableReadyValueRemainsReady() {
        val result = assertIs<Poll.Ready<Triple<Int?, Int, List<Future<Int?>>>>>(selectAll(listOf(ready<Int?>(null))).poll(TaskContext())).value
        assertNull(result.first)
        assertEquals(0, result.second)
        assertTrue(result.third.isEmpty())
    }

    @Test
    fun entirelyPendingScanPollsEachFutureOnceWithoutRemovingAny() {
        val trace = mutableListOf<Int>()
        val futures = (0..2).map { StepFuture(it, it, false, trace) }
        val future = selectAll(futures)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(listOf(0, 1, 2), trace)
        assertEquals(futures, future.intoInner())
    }
}
