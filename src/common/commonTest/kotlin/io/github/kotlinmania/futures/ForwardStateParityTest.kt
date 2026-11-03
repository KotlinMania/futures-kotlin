package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ForwardStateParityTest {
    private class Input(
        events: List<Poll<Yield<Try<Int?, String>>>>,
    ) : Stream<Try<Int?, String>> {
        private val events = events.toMutableList()
        var polls = 0
            private set

        override fun pollNext(context: TaskContext): Poll<Yield<Try<Int?, String>>> {
            polls += 1
            val event = if (events.isEmpty()) Poll.Ready(Yield.End) else events.removeAt(0)
            if (event is Poll.Pending) context.wakeByRef()
            return event
        }
    }

    private class Output(
        ready: List<Poll<SinkOutcome<String>>> = emptyList(),
        sends: List<SinkOutcome<String>> = emptyList(),
        flushes: List<Poll<SinkOutcome<String>>> = emptyList(),
        closes: List<Poll<SinkOutcome<String>>> = emptyList(),
    ) : Sink<Int?, String> {
        private val ready = ready.toMutableList()
        private val sends = sends.toMutableList()
        private val flushes = flushes.toMutableList()
        private val closes = closes.toMutableList()
        val attempts = mutableListOf<Int?>()
        var readyPolls = 0
            private set
        var flushPolls = 0
            private set
        var closePolls = 0
            private set

        private fun poll(events: MutableList<Poll<SinkOutcome<String>>>, context: TaskContext): Poll<SinkOutcome<String>> {
            val event = if (events.isEmpty()) Poll.Ready(SinkOutcome.Ready) else events.removeAt(0)
            if (event is Poll.Pending) context.wakeByRef()
            return event
        }

        override fun pollReady(context: TaskContext): Poll<SinkOutcome<String>> {
            readyPolls += 1
            return poll(ready, context)
        }

        override fun startSend(item: Int?): SinkOutcome<String> {
            attempts.add(item)
            return if (sends.isEmpty()) SinkOutcome.Ready else sends.removeAt(0)
        }

        override fun pollFlush(context: TaskContext): Poll<SinkOutcome<String>> {
            flushPolls += 1
            return poll(flushes, context)
        }

        override fun pollClose(context: TaskContext): Poll<SinkOutcome<String>> {
            closePolls += 1
            return poll(closes, context)
        }
    }

    private fun ok(item: Int?): Poll<Yield<Try<Int?, String>>> = Poll.Ready(Yield.Value(Try.Ok(item)))

    @Test
    fun nullItemIsSentBeforeTheNextItemAndClose() {
        val input = Input(listOf(ok(null), ok(7), Poll.Ready(Yield.End)))
        val output = Output()
        val future = input.forward(output)
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(TaskContext()))
        assertEquals(listOf(null, 7), output.attempts)
        assertEquals(2, output.readyPolls)
        assertEquals(1, output.closePolls)
        assertTrue(future.isTerminated())
    }

    @Test
    fun pendingReadinessRetainsNullWithoutReadingAhead() {
        val input = Input(listOf(ok(null), ok(7), Poll.Ready(Yield.End)))
        val output = Output(ready = listOf(Poll.Pending))
        val future = input.forward(output)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(1, input.polls)
        assertEquals(emptyList(), output.attempts)
        assertEquals(0, output.closePolls)
        assertFalse(future.isTerminated())
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(TaskContext()))
        assertEquals(listOf(null, 7), output.attempts)
        assertEquals(3, input.polls)
        assertTrue(future.isTerminated())
    }

    @Test
    fun closeErrorDoesNotMarkForwardTerminated() {
        val input = Input(listOf(Poll.Ready(Yield.End)))
        val output = Output(closes = listOf(Poll.Ready(SinkOutcome.Err("close"))))
        val future = input.forward(output)
        assertEquals(Poll.Ready(Try.Err("close")), future.poll(TaskContext()))
        assertFalse(future.isTerminated())
        assertEquals(1, input.polls)
        assertEquals(1, output.closePolls)
    }

    @Test
    fun closePendingThenSuccessUsesFusedInputAndTerminatesOnlyOnSuccess() {
        val input = Input(listOf(Poll.Ready(Yield.End)))
        val output = Output(closes = listOf(Poll.Pending, Poll.Ready(SinkOutcome.Ready)))
        val future = input.forward(output)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertFalse(future.isTerminated())
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(TaskContext()))
        assertTrue(future.isTerminated())
        assertEquals(1, input.polls)
        assertEquals(2, output.closePolls)
    }

    @Test
    fun readinessErrorReturnsBeforeSendAndCloseWithoutTerminating() {
        val input = Input(listOf(ok(1), Poll.Ready(Yield.End)))
        val output = Output(ready = listOf(Poll.Ready(SinkOutcome.Err("ready"))))
        val future = input.forward(output)
        assertEquals(Poll.Ready(Try.Err("ready")), future.poll(TaskContext()))
        assertEquals(emptyList(), output.attempts)
        assertEquals(1, input.polls)
        assertEquals(0, output.closePolls)
        assertFalse(future.isTerminated())
    }

    @Test
    fun sendErrorReturnsBeforeFurtherInputAndCloseWithoutTerminating() {
        val input = Input(listOf(ok(1), Poll.Ready(Yield.End)))
        val output = Output(sends = listOf(SinkOutcome.Err("send")))
        val future = input.forward(output)
        assertEquals(Poll.Ready(Try.Err("send")), future.poll(TaskContext()))
        assertEquals(listOf<Int?>(1), output.attempts)
        assertEquals(1, input.polls)
        assertEquals(0, output.closePolls)
        assertFalse(future.isTerminated())
    }

    @Test
    fun pendingInputFlushErrorDoesNotCloseOrTerminate() {
        val input = Input(listOf(Poll.Pending, Poll.Ready(Yield.End)))
        val output = Output(flushes = listOf(Poll.Ready(SinkOutcome.Err("flush"))))
        val future = input.forward(output)
        assertEquals(Poll.Ready(Try.Err("flush")), future.poll(TaskContext()))
        assertEquals(1, output.flushPolls)
        assertEquals(0, output.closePolls)
        assertEquals(1, input.polls)
        assertFalse(future.isTerminated())
    }
}
