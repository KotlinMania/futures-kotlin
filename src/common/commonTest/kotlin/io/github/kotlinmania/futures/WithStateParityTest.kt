package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.sink.With
import io.github.kotlinmania.futures.sink.with
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class WithStateParityTest {
    private class Output : Sink<Int?, String?> {
        val trace = mutableListOf<String>()
        val attempts = mutableListOf<Int?>()
        val ready = mutableListOf<Poll<SinkOutcome<String?>>>()
        val sends = mutableListOf<SinkOutcome<String?>>()
        val flushes = mutableListOf<Poll<SinkOutcome<String?>>>()
        val closes = mutableListOf<Poll<SinkOutcome<String?>>>()

        private fun event(
            events: MutableList<Poll<SinkOutcome<String?>>>,
            context: TaskContext,
        ): Poll<SinkOutcome<String?>> {
            val result = if (events.isEmpty()) Poll.Ready(SinkOutcome.Ready) else events.removeAt(0)
            if (result === Poll.Pending) context.wakeByRef()
            return result
        }

        override fun pollReady(context: TaskContext): Poll<SinkOutcome<String?>> {
            trace.add("ready")
            return event(ready, context)
        }

        override fun startSend(item: Int?): SinkOutcome<String?> {
            trace.add("send:${item ?: "null"}")
            attempts.add(item)
            return if (sends.isEmpty()) SinkOutcome.Ready else sends.removeAt(0)
        }

        override fun pollFlush(context: TaskContext): Poll<SinkOutcome<String?>> {
            trace.add("flush")
            return event(flushes, context)
        }

        override fun pollClose(context: TaskContext): Poll<SinkOutcome<String?>> {
            trace.add("close")
            return event(closes, context)
        }
    }

    private class TransformFuture(
        private val output: Output,
        events: List<Poll<Try<Int?, String?>>>,
    ) : Future<Try<Int?, String?>> {
        private val events = events.toMutableList()

        override fun poll(context: TaskContext): Poll<Try<Int?, String?>> {
            output.trace.add("future")
            check(events.isNotEmpty()) { "unexpected future repoll" }
            val result = events.removeAt(0)
            if (result === Poll.Pending) context.wakeByRef()
            return result
        }
    }

    private fun mapped(
        output: Output,
        events: List<Poll<Try<Int?, String?>>>,
    ): With<Int?, Int, String?> =
        output.with { item: Int ->
            output.trace.add("transform:$item")
            TransformFuture(output, events)
        }

    @Test
    fun idleReadinessControl() {
        val output = Output()
        output.ready.add(Poll.Pending)
        val sink = mapped(output, emptyList())
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertSame(Poll.Pending, sink.pollReady(context))
        assertEquals(listOf("ready"), output.trace)
        assertEquals(1, wakes)
    }

    @Test
    fun readySendsBeforeNextReservation() {
        val output = Output()
        val sink = mapped(output, listOf(Poll.Ready(Try.Ok(7))))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(listOf("ready", "transform:1", "future", "send:7", "ready"), output.trace)
    }

    @Test
    fun flushDoesNotReserveAgain() {
        val output = Output()
        output.ready.addAll(listOf(Poll.Ready(SinkOutcome.Ready), Poll.Pending))
        val sink = mapped(output, listOf(Poll.Ready(Try.Ok(7))))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(TaskContext()))
        assertEquals(listOf("ready", "transform:1", "future", "send:7", "flush"), output.trace)
        assertEquals(listOf<Int?>(7), output.attempts)
    }

    @Test
    fun nullOutputIsSent() {
        val output = Output()
        val sink = mapped(output, listOf(Poll.Ready(Try.Ok(null))))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(TaskContext()))
        assertEquals(listOf("ready", "transform:1", "future", "send:null", "flush"), output.trace)
        assertEquals(listOf<Int?>(null), output.attempts)
    }

    @Test
    fun pendingFutureIsRetained() {
        val output = Output()
        val sink = mapped(output, listOf(Poll.Pending, Poll.Pending, Poll.Ready(Try.Ok(7))))
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(context))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertSame(Poll.Pending, sink.pollFlush(context))
        assertSame(Poll.Pending, sink.pollFlush(context))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(context))
        assertEquals(listOf("ready", "transform:1", "future", "future", "future", "send:7", "flush"), output.trace)
        assertEquals(2, wakes)
    }

    @Test
    fun overlappingSendPanicsBeforeTransform() {
        val output = Output()
        val sink =
            output.with { item: Int ->
                output.trace.add("transform:$item")
                TransformFuture(output, listOf(Poll.Ready(Try.Ok(item))))
            }
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertFailsWith<IllegalStateException> { sink.startSend(2) }
        assertEquals(listOf("ready", "transform:1"), output.trace)
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(TaskContext()))
        assertEquals(listOf("ready", "transform:1", "future", "send:1", "flush"), output.trace)
    }

    @Test
    fun sendErrorConsumesFuture() {
        val output = Output()
        output.sends.add(SinkOutcome.Err("send"))
        val sink = mapped(output, listOf(Poll.Ready(Try.Ok(7))))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(Poll.Ready(SinkOutcome.Err("send")), sink.pollFlush(TaskContext()))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(TaskContext()))
        assertEquals(listOf("ready", "transform:1", "future", "send:7", "flush"), output.trace)
    }

    @Test
    fun transformErrorRetainsActiveFuture() {
        val output = Output()
        val sink = mapped(output, listOf(Poll.Ready(Try.Err("transform"))))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(Poll.Ready(SinkOutcome.Err("transform")), sink.pollFlush(TaskContext()))
        assertFailsWith<IllegalStateException> { sink.startSend(2) }
        assertEquals(listOf("ready", "transform:1", "future"), output.trace)
        assertEquals(emptyList(), output.attempts)
    }

    @Test
    fun nullTransformErrorDelaysClose() {
        val output = Output()
        val sink = mapped(output, listOf(Poll.Pending, Poll.Ready(Try.Err(null))))
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(context))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertSame(Poll.Pending, sink.pollClose(context))
        assertEquals(Poll.Ready(SinkOutcome.Err(null)), sink.pollClose(context))
        assertEquals(listOf("ready", "transform:1", "future", "future"), output.trace)
        assertEquals(1, wakes)
    }

    @Test
    fun nextReadinessErrorOccursAfterSend() {
        val output = Output()
        output.ready.addAll(listOf(Poll.Ready(SinkOutcome.Ready), Poll.Ready(SinkOutcome.Err("ready"))))
        val sink = mapped(output, listOf(Poll.Ready(Try.Ok(7))))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(Poll.Ready(SinkOutcome.Err("ready")), sink.pollReady(TaskContext()))
        assertEquals(listOf("ready", "transform:1", "future", "send:7", "ready"), output.trace)
        assertEquals(listOf<Int?>(7), output.attempts)
    }

    @Test
    fun flushErrorFollowsSuccessfulSend() {
        val output = Output()
        output.flushes.add(Poll.Ready(SinkOutcome.Err(null)))
        val sink = mapped(output, listOf(Poll.Ready(Try.Ok(7))))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(Poll.Ready(SinkOutcome.Err(null)), sink.pollFlush(TaskContext()))
        assertEquals(listOf("ready", "transform:1", "future", "send:7", "flush"), output.trace)
    }

    @Test
    fun closePendingDoesNotRepollFuture() {
        val output = Output()
        output.closes.addAll(listOf(Poll.Pending, Poll.Ready(SinkOutcome.Err("close"))))
        val sink = mapped(output, listOf(Poll.Ready(Try.Ok(7))))
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(context))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertSame(Poll.Pending, sink.pollClose(context))
        assertEquals(Poll.Ready(SinkOutcome.Err("close")), sink.pollClose(context))
        assertEquals(listOf("ready", "transform:1", "future", "send:7", "close", "close"), output.trace)
        assertEquals(1, wakes)
    }
}
