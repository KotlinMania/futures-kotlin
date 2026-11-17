package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.sink.sendAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SendAllStateParityTest {
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
    fun repeatedPendingReadinessRetainsNullBeforeReadingTheNextItem() {
        val input = Input(listOf(ok(null), ok(7), Poll.Ready(Yield.End)))
        val output = Output(ready = listOf(Poll.Pending, Poll.Pending, Poll.Ready(SinkOutcome.Ready)))
        val future = output.sendAll(input)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(1, input.polls)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(1, input.polls)
        assertEquals(emptyList(), output.attempts)
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(TaskContext()))
        assertEquals(listOf(null, 7), output.attempts)
        assertEquals(3, input.polls)
        assertEquals(1, output.flushPolls)
        assertEquals(0, output.closePolls)
    }

    @Test
    fun readinessErrorOnBufferedNullPrecedesAnyFurtherInput() {
        val input = Input(listOf(ok(null), ok(7), Poll.Ready(Yield.End)))
        val output = Output(ready = listOf(Poll.Pending, Poll.Ready(SinkOutcome.Err("ready"))))
        val future = output.sendAll(input)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(Poll.Ready(Try.Err("ready")), future.poll(TaskContext()))
        assertEquals(1, input.polls)
        assertEquals(emptyList(), output.attempts)
        assertEquals(0, output.flushPolls)
        assertEquals(0, output.closePolls)
    }

    @Test
    fun sendErrorOnBufferedNullPrecedesAnyFurtherInput() {
        val input = Input(listOf(ok(null), ok(7), Poll.Ready(Yield.End)))
        val output = Output(ready = listOf(Poll.Pending), sends = listOf(SinkOutcome.Err("send")))
        val future = output.sendAll(input)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(Poll.Ready(Try.Err("send")), future.poll(TaskContext()))
        assertEquals(listOf<Int?>(null), output.attempts)
        assertEquals(1, input.polls)
        assertEquals(0, output.flushPolls)
        assertEquals(0, output.closePolls)
    }

    @Test
    fun eofFlushPendingThenSuccessDoesNotRepollEndedInputOrCloseSink() {
        val input = Input(listOf(Poll.Ready(Yield.End), ok(99)))
        val output = Output(flushes = listOf(Poll.Pending, Poll.Ready(SinkOutcome.Ready)))
        val future = output.sendAll(input)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(TaskContext()))
        assertEquals(1, input.polls)
        assertEquals(emptyList(), output.attempts)
        assertEquals(2, output.flushPolls)
        assertEquals(0, output.closePolls)
    }

    @Test
    fun pendingInputFlushesButDoesNotCompleteThenReadyNullIsSent() {
        val input = Input(listOf(Poll.Pending, ok(null), Poll.Ready(Yield.End)))
        val output = Output()
        val future = output.sendAll(input)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(1, input.polls)
        assertEquals(1, output.flushPolls)
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(TaskContext()))
        assertEquals(listOf<Int?>(null), output.attempts)
        assertEquals(3, input.polls)
        assertEquals(2, output.flushPolls)
        assertEquals(0, output.closePolls)
    }

    @Test
    fun inputErrorAfterSendReturnsWithoutFlushOrClose() {
        val input = Input(listOf(ok(null), Poll.Ready(Yield.Value(Try.Err("input")))))
        val output = Output()
        val future = output.sendAll(input)
        assertEquals(Poll.Ready(Try.Err("input")), future.poll(TaskContext()))
        assertEquals(listOf<Int?>(null), output.attempts)
        assertEquals(2, input.polls)
        assertEquals(0, output.flushPolls)
        assertEquals(0, output.closePolls)
    }

    @Test
    fun eofFlushErrorReturnsWithoutClosingSink() {
        val input = Input(listOf(Poll.Ready(Yield.End)))
        val output = Output(flushes = listOf(Poll.Ready(SinkOutcome.Err("flush"))))
        val future = output.sendAll(input)
        assertEquals(Poll.Ready(Try.Err("flush")), future.poll(TaskContext()))
        assertEquals(1, input.polls)
        assertEquals(1, output.flushPolls)
        assertEquals(0, output.closePolls)
    }
}
