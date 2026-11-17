package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.sink.WithFlatMap
import io.github.kotlinmania.futures.sink.withFlatMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class WithFlatMapStateParityTest {
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

    private class Input(
        private val output: Output,
        events: List<Poll<Yield<Try<Int?, String?>>>>,
    ) : Stream<Try<Int?, String?>> {
        private val events = events.toMutableList()

        override fun pollNext(context: TaskContext): Poll<Yield<Try<Int?, String?>>> {
            output.trace.add("next")
            val result = if (events.isEmpty()) Poll.Ready(Yield.End) else events.removeAt(0)
            if (result === Poll.Pending) context.wakeByRef()
            return result
        }
    }

    private fun value(item: Int?): Poll<Yield<Try<Int?, String?>>> = Poll.Ready(Yield.Value(Try.Ok(item)))

    private fun mapped(
        output: Output,
        events: List<Poll<Yield<Try<Int?, String?>>>>,
    ): WithFlatMap<Int?, Int, String?> =
        output.withFlatMap { item: Int ->
            output.trace.add("transform:$item")
            Input(output, events)
        }

    @Test
    fun idleReadinessDoesNotPollInner() {
        val output = Output()
        output.ready.add(Poll.Pending)
        val sink = mapped(output, emptyList())
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(emptyList(), output.trace)
    }

    @Test
    fun nonnullDrainHasNoExtraReadinessPoll() {
        val output = Output()
        val sink = mapped(output, listOf(value(7)))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(listOf("transform:1", "next", "ready", "send:7", "next"), output.trace)
    }

    @Test
    fun nullableItemSurvivesRepeatedPending() {
        val output = Output()
        output.ready.addAll(listOf(Poll.Pending, Poll.Pending))
        val sink = mapped(output, listOf(value(null)))
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertSame(Poll.Pending, sink.pollReady(context))
        assertSame(Poll.Pending, sink.pollReady(context))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(context))
        assertEquals(listOf("transform:1", "next", "ready", "ready", "ready", "send:null", "next"), output.trace)
        assertEquals(listOf<Int?>(null), output.attempts)
        assertEquals(2, wakes)
    }

    @Test
    fun freshReadinessErrorDropsItem() {
        val output = Output()
        output.ready.add(Poll.Ready(SinkOutcome.Err("ready")))
        val sink = mapped(output, listOf(value(7), value(8)))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(Poll.Ready(SinkOutcome.Err("ready")), sink.pollReady(TaskContext()))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(listOf("transform:1", "next", "ready", "next", "ready", "send:8", "next"), output.trace)
        assertEquals(listOf<Int?>(8), output.attempts)
    }

    @Test
    fun bufferedReadinessErrorRetainsItem() {
        val output = Output()
        output.ready.addAll(listOf(Poll.Pending, Poll.Ready(SinkOutcome.Err(null))))
        val sink = mapped(output, listOf(value(null)))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertSame(Poll.Pending, sink.pollReady(TaskContext()))
        assertEquals(Poll.Ready(SinkOutcome.Err(null)), sink.pollReady(TaskContext()))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(listOf("transform:1", "next", "ready", "ready", "ready", "send:null", "next"), output.trace)
    }

    @Test
    fun freshSendErrorConsumesItem() {
        val output = Output()
        output.sends.add(SinkOutcome.Err("send"))
        val sink = mapped(output, listOf(value(7), value(8)))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(Poll.Ready(SinkOutcome.Err("send")), sink.pollReady(TaskContext()))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(TaskContext()))
        assertEquals(
            listOf("transform:1", "next", "ready", "send:7", "next", "ready", "send:8", "next", "flush"),
            output.trace,
        )
    }

    @Test
    fun bufferedSendErrorConsumesItem() {
        val output = Output()
        output.ready.add(Poll.Pending)
        output.sends.add(SinkOutcome.Err("send"))
        val sink = mapped(output, listOf(value(null), value(8)))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertSame(Poll.Pending, sink.pollReady(TaskContext()))
        assertEquals(Poll.Ready(SinkOutcome.Err("send")), sink.pollReady(TaskContext()))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(TaskContext()))
        assertEquals(
            listOf("transform:1", "next", "ready", "ready", "send:null", "next", "ready", "send:8", "next", "flush"),
            output.trace,
        )
    }

    @Test
    fun streamErrorDoesNotPollSink() {
        val output = Output()
        val sink = mapped(output, listOf(Poll.Ready(Yield.Value(Try.Err(null))), value(8)))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(Poll.Ready(SinkOutcome.Err(null)), sink.pollReady(TaskContext()))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(TaskContext()))
        assertEquals(listOf("transform:1", "next", "next", "ready", "send:8", "next", "flush"), output.trace)
    }

    @Test
    fun streamPendingDelaysFlush() {
        val output = Output()
        val sink = mapped(output, listOf(Poll.Pending, value(null)))
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertSame(Poll.Pending, sink.pollFlush(context))
        assertEquals(listOf("transform:1", "next"), output.trace)
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(context))
        assertEquals(listOf("transform:1", "next", "next", "ready", "send:null", "next", "flush"), output.trace)
        assertEquals(1, wakes)
    }

    @Test
    fun bufferedItemDelaysClose() {
        val output = Output()
        output.ready.add(Poll.Pending)
        output.closes.add(Poll.Pending)
        val sink = mapped(output, listOf(value(null)))
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertSame(Poll.Pending, sink.pollClose(context))
        assertSame(Poll.Pending, sink.pollClose(context))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollClose(context))
        assertEquals(listOf("transform:1", "next", "ready", "ready", "send:null", "next", "close", "close"), output.trace)
        assertEquals(2, wakes)
    }

    @Test
    fun overlappingStartSendPanicsBeforeTransform() {
        val output = Output()
        val sink =
            output.withFlatMap { item: Int ->
                output.trace.add("transform:$item")
                Input(output, listOf(value(item)))
            }
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertFailsWith<IllegalStateException> { sink.startSend(2) }
        assertEquals(listOf("transform:1"), output.trace)
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(TaskContext()))
        assertEquals(listOf("transform:1", "next", "ready", "send:1", "next", "flush"), output.trace)
    }

    @Test
    fun transformPanicLeavesNoActiveStream() {
        val output = Output()
        val sink =
            output.withFlatMap { item: Int ->
                if (item == 1) {
                    output.trace.add("panic:1")
                    error("transform panic")
                }
                output.trace.add("transform:$item")
                Input(output, listOf(value(item)))
            }
        assertFailsWith<IllegalStateException> { sink.startSend(1) }
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(listOf("panic:1"), output.trace)
        assertEquals(SinkOutcome.Ready, sink.startSend(2))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(TaskContext()))
        assertEquals(listOf("panic:1", "transform:2", "next", "ready", "send:2", "next", "flush"), output.trace)
    }
}
