package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UnorderedBufferPollingParityTest {
    private class GateFuture<T>(
        private val id: Int,
        private val value: T,
        private var released: Boolean,
        private val trace: MutableList<Int>,
    ) : Future<T> {
        private var completed = false
        private var waker: Waker? = null

        fun release() {
            released = true
            waker?.wakeByRef()
        }

        override fun poll(context: TaskContext): Poll<T> {
            check(!completed) { "fixture polled after completion" }
            trace.add(id)
            if (!released) {
                waker = context.waker
                return Poll.Pending
            }
            completed = true
            return Poll.Ready(value)
        }
    }

    private class Input<T>(
        events: List<Poll<Yield<T>>>,
    ) : Stream<T> {
        private val events = events.toMutableList()
        private var ended = false
        var polls = 0
            private set

        override fun pollNext(context: TaskContext): Poll<Yield<T>> {
            check(!ended) { "input polled after end" }
            polls += 1
            val event = events.removeAt(0)
            if (event == Poll.Ready(Yield.End)) ended = true
            if (event is Poll.Pending) context.wakeByRef()
            return event
        }

        override fun sizeHint(): SizeHint {
            check(!ended) { "ended input hint must come from Fuse" }
            val count = events.count { it is Poll.Ready && it.value is Yield.Value }
            return SizeHint(count, count)
        }
    }

    private fun <T> value(value: T): Poll<Yield<T>> = Poll.Ready(Yield.Value(value))

    private fun <T> item(poll: Poll<Yield<T>>): T =
        assertIs<Yield.Value<T>>(assertIs<Poll.Ready<Yield<T>>>(poll).value).value

    @Test
    fun idleUnorderedFuturesAreNotRepolledWithoutWake() {
        val trace = mutableListOf<Int>()
        val first = GateFuture(0, 1, false, trace)
        val second = GateFuture(1, 2, false, trace)
        val stream = Input(listOf(value<Future<Int>>(first), value(second))).bufferUnordered(2)
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertEquals(listOf(0, 1), trace)
    }

    @Test
    fun onlyTheWokenFutureIsPolledAndItsNullOutputStaysPresent() {
        val trace = mutableListOf<Int>()
        val first = GateFuture<Int?>(0, 7, false, trace)
        val second = GateFuture<Int?>(1, null, false, trace)
        val stream = Input(listOf(value<Future<Int?>>(first), value(second), Poll.Ready(Yield.End))).bufferUnordered(2)
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        second.release()
        assertNull(item(stream.pollNext(TaskContext())))
        assertEquals(listOf(0, 1, 1), trace)
        first.release()
        assertEquals(7, item(stream.pollNext(TaskContext())))
    }

    @Test
    fun zeroCapacityDoesNotPollUnorderedInput() {
        val input = Input<Future<Int>>(listOf(Poll.Ready(Yield.End)))
        val stream = input.bufferUnordered(0)
        repeat(2) { assertSame(Poll.Pending, stream.pollNext(TaskContext())) }
        assertEquals(0, input.polls)
    }

    @Test
    fun endedUnorderedInputHasFusedZeroHint() {
        val stream = Input<Future<Int>>(listOf(Poll.Ready(Yield.End))).bufferUnordered(2)
        assertSame(Yield.End, assertIs<Poll.Ready<Yield<Int>>>(stream.pollNext(TaskContext())).value)
        assertEquals(SizeHint(0, 0), stream.sizeHint())
        assertTrue(stream.isTerminated())
    }

    @Test
    fun fallibleUnorderedQueuePollsOnlyWokenFutureAndYieldsItsErrorFirst() {
        val trace = mutableListOf<Int>()
        val first = GateFuture<Try<Int?, String?>>(0, Try.Ok(null), false, trace)
        val second = GateFuture<Try<Int?, String?>>(1, Try.Err(null), false, trace)
        val input = Input(listOf(value<Try<Future<Try<Int?, String?>>, String?>>(Try.Ok(first)), value(Try.Ok(second)), Poll.Ready(Yield.End)))
        val stream = input.asTryStream().tryBufferUnordered(2)
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertEquals(listOf(0, 1), trace)
        second.release()
        assertNull(assertIs<Try.Err<String?>>(item(stream.pollNext(TaskContext()))).error)
        assertEquals(listOf(0, 1, 1), trace)
        first.release()
        assertNull(assertIs<Try.Ok<Int?>>(item(stream.pollNext(TaskContext()))).value)
    }

    @Test
    fun outerErrorPrecedesFuturePollsAndQueueResumesAfterwards() {
        val trace = mutableListOf<Int>()
        val first = GateFuture<Try<Int?, String?>>(0, Try.Ok(1), false, trace)
        val second = GateFuture<Try<Int?, String?>>(1, Try.Err("future"), true, trace)
        val input = Input(listOf(value<Try<Future<Try<Int?, String?>>, String?>>(Try.Ok(first)), value(Try.Err(null)), value(Try.Ok(second)), Poll.Ready(Yield.End)))
        val stream = input.asTryStream().tryBufferUnordered(2)
        assertNull(assertIs<Try.Err<String?>>(item(stream.pollNext(TaskContext()))).error)
        assertTrue(trace.isEmpty())
        assertEquals("future", assertIs<Try.Err<String?>>(item(stream.pollNext(TaskContext()))).error)
        first.release()
        assertEquals(1, assertIs<Try.Ok<Int?>>(item(stream.pollNext(TaskContext()))).value)
    }

    @Test
    fun zeroCapacityDoesNotPollEvenAFallibleInputError() {
        val input = Input<Try<Future<Try<Int?, String?>>, String?>>(listOf(value(Try.Err("unpolled")), Poll.Ready(Yield.End)))
        val stream = input.asTryStream().tryBufferUnordered(0)
        repeat(2) { assertSame(Poll.Pending, stream.pollNext(TaskContext())) }
        assertEquals(0, input.polls)
    }

    @Test
    fun fallibleUnorderedSizeHintUsesUpstreamDefault() {
        val input = Input<Try<Future<Try<Int?, String?>>, String?>>(listOf(value(Try.Err(null)), Poll.Ready(Yield.End)))
        val stream = input.asTryStream().tryBufferUnordered(2)
        assertEquals(SizeHint(0, null), stream.sizeHint())
        assertNull(assertIs<Try.Err<String?>>(item(stream.pollNext(TaskContext()))).error)
        assertEquals(SizeHint(0, null), stream.sizeHint())
    }
}
