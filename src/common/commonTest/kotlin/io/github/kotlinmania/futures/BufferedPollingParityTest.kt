package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BufferedPollingParityTest {
    private class GateFuture(
        private val id: Int,
        private val value: Int?,
        private var released: Boolean,
        private val trace: MutableList<Int>,
    ) : Future<Int?> {
        private var completed = false
        private var waker: Waker? = null
        var polls = 0
            private set

        fun release() {
            released = true
            waker?.wakeByRef()
        }

        override fun poll(context: TaskContext): Poll<Int?> {
            check(!completed) { "fixture polled after completion" }
            trace.add(id)
            polls += 1
            if (!released) {
                waker = context.waker
                return Poll.Pending
            }
            completed = true
            return Poll.Ready(value)
        }
    }

    private class Input(
        events: List<Poll<Yield<Future<Int?>>>>,
    ) : Stream<Future<Int?>> {
        private val events = events.toMutableList()
        private var ended = false
        var polls = 0
            private set

        override fun pollNext(context: TaskContext): Poll<Yield<Future<Int?>>> {
            check(!ended) { "input polled after end" }
            polls += 1
            val event = events.removeAt(0)
            if (event == Poll.Ready(Yield.End)) ended = true
            if (event is Poll.Pending) context.wakeByRef()
            return event
        }

        override fun sizeHint(): SizeHint {
            check(!ended) { "ended input hint must be supplied by Fuse" }
            val count = events.count { it is Poll.Ready && it.value is Yield.Value }
            return SizeHint(count, count)
        }
    }

    private fun value(future: Future<Int?>): Poll<Yield<Future<Int?>>> = Poll.Ready(Yield.Value(future))

    private fun item(poll: Poll<Yield<Int?>>): Int? =
        assertIs<Yield.Value<Int?>>(assertIs<Poll.Ready<Yield<Int?>>>(poll).value).value

    @Test
    fun laterFuturesProgressWhileTheFirstWaitsButResultsStayInInputOrder() {
        val trace = mutableListOf<Int>()
        val first = GateFuture(0, 1, false, trace)
        val second = GateFuture(1, 2, true, trace)
        val third = GateFuture(2, 3, true, trace)
        val input = Input(listOf(value(first), value(second), value(third), Poll.Ready(Yield.End)))
        val stream = input.buffered(3)
        val context = TaskContext()
        assertSame(Poll.Pending, stream.pollNext(context))
        assertEquals(listOf(0, 1, 2), trace)
        first.release()
        assertEquals(1, item(stream.pollNext(context)))
        assertEquals(2, item(stream.pollNext(context)))
        assertEquals(3, item(stream.pollNext(context)))
        assertSame(Yield.End, assertIs<Poll.Ready<Yield<Int?>>>(stream.pollNext(context)).value)
        assertEquals(listOf(0, 1, 2, 0), trace)
    }

    @Test
    fun completedOutOfOrderOutputsStillOccupyCapacity() {
        val trace = mutableListOf<Int>()
        val first = GateFuture(0, 1, false, trace)
        val second = GateFuture(1, 2, true, trace)
        val third = GateFuture(2, 3, true, trace)
        val input = Input(listOf(value(first), value(second), value(third), Poll.Ready(Yield.End)))
        val stream = input.buffered(2)
        val context = TaskContext()
        assertSame(Poll.Pending, stream.pollNext(context))
        assertEquals(1, second.polls)
        assertEquals(2, input.polls)
        assertSame(Poll.Pending, stream.pollNext(context))
        assertEquals(2, input.polls)
        assertEquals(0, third.polls)
        first.release()
        assertEquals(1, item(stream.pollNext(context)))
        assertEquals(2, item(stream.pollNext(context)))
        assertEquals(3, input.polls)
        assertEquals(0, third.polls)
        assertEquals(3, item(stream.pollNext(context)))
    }

    @Test
    fun nullableFirstOutputIsNotConfusedWithTheEmptyQueue() {
        val trace = mutableListOf<Int>()
        val first = GateFuture(0, null, false, trace)
        val second = GateFuture(1, 7, true, trace)
        val stream = Input(listOf(value(first), value(second), Poll.Ready(Yield.End))).buffered(2)
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertEquals(1, second.polls)
        first.release()
        assertNull(item(stream.pollNext(TaskContext())))
        assertEquals(7, item(stream.pollNext(TaskContext())))
        assertEquals(1, second.polls)
    }

    @Test
    fun zeroCapacityStaysPendingWithoutPollingInput() {
        val input = Input(listOf(Poll.Ready(Yield.End)))
        val stream = input.buffered(0)
        repeat(2) { assertSame(Poll.Pending, stream.pollNext(TaskContext())) }
        assertEquals(0, input.polls)
    }

    @Test
    fun outerPendingDoesNotTerminateAnEmptyOrderedQueue() {
        val input = Input(listOf(Poll.Pending, value(ready<Int?>(5)), Poll.Ready(Yield.End)))
        val stream = input.buffered(2)
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertEquals(5, item(stream.pollNext(TaskContext())))
        assertSame(Yield.End, assertIs<Poll.Ready<Yield<Int?>>>(stream.pollNext(TaskContext())).value)
        assertTrue(stream.isTerminated())
    }

    @Test
    fun endedInputUsesFusedZeroSizeHints() {
        val stream = Input(listOf(Poll.Ready(Yield.End))).buffered(2)
        assertSame(Yield.End, assertIs<Poll.Ready<Yield<Int?>>>(stream.pollNext(TaskContext())).value)
        assertEquals(SizeHint(0, 0), stream.sizeHint())
        assertTrue(stream.isTerminated())
    }
}
