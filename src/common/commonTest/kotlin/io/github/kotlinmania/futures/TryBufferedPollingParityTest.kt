package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TryBufferedPollingParityTest {
    private class GateFuture(
        private val id: Int,
        private val value: Try<Int?, String?>,
        private var released: Boolean,
        private val trace: MutableList<Int>,
    ) : Future<Try<Int?, String?>> {
        private var completed = false
        private var waker: Waker? = null
        var polls = 0
            private set

        fun release() {
            released = true
            waker?.wakeByRef()
        }

        override fun poll(context: TaskContext): Poll<Try<Int?, String?>> {
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
        events: List<Poll<Yield<Try<Future<Try<Int?, String?>>, String?>>>>,
    ) : TryStream<Future<Try<Int?, String?>>, String?> {
        private val events = events.toMutableList()
        private var ended = false
        var polls = 0
            private set

        override fun pollNext(context: TaskContext): Poll<Yield<Try<Future<Try<Int?, String?>>, String?>>> {
            check(!ended) { "input polled after end" }
            polls += 1
            val event = events.removeAt(0)
            if (event == Poll.Ready(Yield.End)) ended = true
            if (event is Poll.Pending) context.wakeByRef()
            return event
        }

        override fun sizeHint(): SizeHint {
            val count = events.count { it is Poll.Ready && it.value is Yield.Value }
            return SizeHint(count, count)
        }
    }

    private fun future(future: Future<Try<Int?, String?>>): Poll<Yield<Try<Future<Try<Int?, String?>>, String?>>> =
        Poll.Ready(Yield.Value(Try.Ok(future)))

    private fun error(error: String?): Poll<Yield<Try<Future<Try<Int?, String?>>, String?>>> =
        Poll.Ready(Yield.Value(Try.Err(error)))

    private fun item(poll: Poll<Yield<Try<Int?, String?>>>): Try<Int?, String?> =
        assertIs<Yield.Value<Try<Int?, String?>>>(assertIs<Poll.Ready<Yield<Try<Int?, String?>>>>(poll).value).value

    @Test
    fun laterFutureErrorIsDrivenButWaitsForNullableFirstSuccess() {
        val trace = mutableListOf<Int>()
        val first = GateFuture(0, Try.Ok(null), false, trace)
        val second = GateFuture(1, Try.Err(null), true, trace)
        val stream = Input(listOf(future(first), future(second), Poll.Ready(Yield.End))).tryBuffered(2)
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertEquals(listOf(0, 1), trace)
        first.release()
        assertNull(assertIs<Try.Ok<Int?>>(item(stream.pollNext(TaskContext()))).value)
        assertNull(assertIs<Try.Err<String?>>(item(stream.pollNext(TaskContext()))).error)
        assertEquals(1, second.polls)
        assertEquals(listOf(0, 1, 0), trace)
    }

    @Test
    fun outerErrorReturnsBeforeAnyQueuedFutureIsPolledAndThenResumes() {
        val trace = mutableListOf<Int>()
        val first = GateFuture(0, Try.Ok(1), true, trace)
        val second = GateFuture(1, Try.Ok(2), true, trace)
        val stream = Input(listOf(future(first), error("outer"), future(second), Poll.Ready(Yield.End))).tryBuffered(2)
        assertEquals("outer", assertIs<Try.Err<String?>>(item(stream.pollNext(TaskContext()))).error)
        assertTrue(trace.isEmpty())
        assertEquals(1, assertIs<Try.Ok<Int?>>(item(stream.pollNext(TaskContext()))).value)
        assertEquals(2, assertIs<Try.Ok<Int?>>(item(stream.pollNext(TaskContext()))).value)
    }

    @Test
    fun bufferedErrorOccupiesCapacityAndOuterErrorHasRefillPriority() {
        val trace = mutableListOf<Int>()
        val first = GateFuture(0, Try.Ok(1), false, trace)
        val second = GateFuture(1, Try.Err(null), true, trace)
        val input = Input(listOf(future(first), future(second), error("outer"), Poll.Ready(Yield.End)))
        val stream = input.tryBuffered(2)
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertEquals(1, second.polls)
        assertEquals(2, input.polls)
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertEquals(2, input.polls)
        first.release()
        assertEquals(1, assertIs<Try.Ok<Int?>>(item(stream.pollNext(TaskContext()))).value)
        assertEquals("outer", assertIs<Try.Err<String?>>(item(stream.pollNext(TaskContext()))).error)
        assertNull(assertIs<Try.Err<String?>>(item(stream.pollNext(TaskContext()))).error)
        assertEquals(1, second.polls)
    }

    @Test
    fun zeroCapacityDoesNotPullEvenAnOuterError() {
        val input = Input(listOf(error("unpolled"), Poll.Ready(Yield.End)))
        val stream = input.tryBuffered(0)
        repeat(2) { assertSame(Poll.Pending, stream.pollNext(TaskContext())) }
        assertEquals(0, input.polls)
    }

    @Test
    fun sizeHintUsesTheUpstreamDefaultEvenForKnownInput() {
        val stream = Input(listOf(future(ok<Int?, String?>(7)), error(null), Poll.Ready(Yield.End))).tryBuffered(2)
        assertEquals(SizeHint(0, null), stream.sizeHint())
        assertNull(assertIs<Try.Err<String?>>(item(stream.pollNext(TaskContext()))).error)
        assertEquals(SizeHint(0, null), stream.sizeHint())
        assertEquals(7, assertIs<Try.Ok<Int?>>(item(stream.pollNext(TaskContext()))).value)
    }

    @Test
    fun outerPendingDoesNotTerminateAnEmptyOrderedQueue() {
        val stream = Input(listOf(Poll.Pending, future(ok<Int?, String?>(5)), Poll.Ready(Yield.End))).tryBuffered(2)
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertEquals(5, assertIs<Try.Ok<Int?>>(item(stream.pollNext(TaskContext()))).value)
        assertSame(Yield.End, assertIs<Poll.Ready<Yield<Try<Int?, String?>>>>(stream.pollNext(TaskContext())).value)
    }
}
