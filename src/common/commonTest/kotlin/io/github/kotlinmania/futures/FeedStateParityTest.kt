package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.sink.feed
import io.github.kotlinmania.futures.sink.send
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FeedStateParityTest {
    private class Output : Sink<Int?, String?> {
        val trace = mutableListOf<String>()
        val attempts = mutableListOf<Int?>()
        val ready = mutableListOf<Poll<SinkOutcome<String?>>>()
        val sends = mutableListOf<SinkOutcome<String?>>()
        val flushes = mutableListOf<Poll<SinkOutcome<String?>>>()
        var readinessPanic = false

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
            if (readinessPanic) {
                readinessPanic = false
                error("ready panic")
            }
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

        override fun pollClose(context: TaskContext): Poll<SinkOutcome<String?>> = error("unexpected close")
    }

    @Test
    fun nullableReady() {
        val output = Output()
        val future = output.feed(null)
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(TaskContext()))
        assertEquals(listOf("ready", "send:null"), output.trace)
        assertEquals(listOf<Int?>(null), output.attempts)
        assertFalse(future.isItemPending())
    }

    @Test
    fun nullablePendingTwice() {
        val output = Output()
        output.ready.addAll(listOf(Poll.Pending, Poll.Pending, Poll.Ready(SinkOutcome.Ready)))
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        val future = output.feed(null)
        repeat(2) {
            assertSame(Poll.Pending, future.poll(context))
            assertTrue(future.isItemPending())
            assertTrue(output.attempts.isEmpty())
        }
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(context))
        assertEquals(listOf("ready", "ready", "ready", "send:null"), output.trace)
        assertEquals(2, wakes)
    }

    @Test
    fun readinessErrorRetainsItem() {
        val output = Output()
        output.ready.add(Poll.Ready(SinkOutcome.Err("ready")))
        val future = output.feed(null)
        assertEquals(Poll.Ready(Try.Err("ready")), future.poll(TaskContext()))
        assertTrue(future.isItemPending())
        assertTrue(output.attempts.isEmpty())
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(TaskContext()))
        assertEquals(listOf("ready", "ready", "send:null"), output.trace)
    }

    @Test
    fun sendErrorConsumesItem() {
        val output = Output()
        output.sends.add(SinkOutcome.Err("send"))
        val future = output.feed(null)
        assertEquals(Poll.Ready(Try.Err("send")), future.poll(TaskContext()))
        assertFalse(future.isItemPending())
        assertFailsWith<IllegalStateException> { future.poll(TaskContext()) }
        assertEquals(listOf("ready", "send:null", "ready"), output.trace)
        assertEquals(listOf<Int?>(null), output.attempts)
    }

    @Test
    fun nullableErrorIsNotSuccess() {
        val output = Output()
        output.ready.add(Poll.Ready(SinkOutcome.Err(null)))
        val future = output.feed(null)
        assertEquals(Poll.Ready(Try.Err(null)), future.poll(TaskContext()))
        assertEquals(listOf("ready"), output.trace)
        assertTrue(output.attempts.isEmpty())
    }

    @Test
    fun nonnullControl() {
        val output = Output()
        assertEquals(Poll.Ready(Try.Ok(Unit)), output.feed(7).poll(TaskContext()))
        assertEquals(listOf("ready", "send:7"), output.trace)
    }

    @Test
    fun completedFeedDefersToReadiness() {
        val output = Output()
        output.ready.addAll(
            listOf(Poll.Ready(SinkOutcome.Ready), Poll.Pending, Poll.Ready(SinkOutcome.Err(null))),
        )
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        val future = output.feed(7)
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(context))
        assertSame(Poll.Pending, future.poll(context))
        assertEquals(Poll.Ready(Try.Err(null)), future.poll(context))
        assertFailsWith<IllegalStateException> { future.poll(context) }
        assertEquals(listOf("ready", "send:7", "ready", "ready", "ready"), output.trace)
        assertEquals(1, wakes)
    }

    @Test
    fun sendErrorRepollDefersToReadiness() {
        val output = Output()
        output.ready.addAll(listOf(Poll.Ready(SinkOutcome.Ready), Poll.Pending))
        output.sends.add(SinkOutcome.Err("send"))
        val future = output.feed(7)
        assertEquals(Poll.Ready(Try.Err("send")), future.poll(TaskContext()))
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertFailsWith<IllegalStateException> { future.poll(TaskContext()) }
        assertEquals(listOf("ready", "send:7", "ready", "ready"), output.trace)
    }

    @Test
    fun sendNullWaitsForFlush() {
        val output = Output()
        output.flushes.add(Poll.Pending)
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        val future = output.send(null)
        assertSame(Poll.Pending, future.poll(context))
        assertEquals(listOf("ready", "send:null", "flush"), output.trace)
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(context))
        assertEquals(listOf("ready", "send:null", "flush", "flush"), output.trace)
        assertEquals(1, wakes)
    }

    @Test
    fun readinessPanicRetainsItem() {
        val output = Output()
        output.readinessPanic = true
        val future = output.feed(null)
        assertFailsWith<IllegalStateException> { future.poll(TaskContext()) }
        assertTrue(future.isItemPending())
        assertTrue(output.attempts.isEmpty())
        assertEquals(Poll.Ready(Try.Ok(Unit)), future.poll(TaskContext()))
        assertEquals(listOf("ready", "ready", "send:null"), output.trace)
    }
}
