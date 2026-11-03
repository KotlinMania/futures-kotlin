package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.sink.unfold
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SinkUnfoldStateParityTest {
    private class Script(
        private val trace: MutableList<String>,
        events: List<Poll<Try<Int?, String?>>>,
    ) : Future<Try<Int?, String?>> {
        private val events = events.toMutableList()

        override fun poll(context: TaskContext): Poll<Try<Int?, String?>> {
            trace.add("future")
            check(events.isNotEmpty()) { "unexpected completed future repoll" }
            val result = events.removeAt(0)
            if (result === Poll.Pending) context.wakeByRef()
            return result
        }
    }

    private fun script(
        trace: MutableList<String>,
        state: Int?,
        item: Int,
        events: List<Poll<Try<Int?, String?>>>,
    ): Script {
        trace.add("call:${state ?: "null"}:$item")
        return Script(trace, events)
    }

    @Test
    fun nullableInitialState() {
        val trace = mutableListOf<String>()
        val sink =
            unfold<Int?, Int, String?>(null) { state, item ->
                script(trace, state, item, listOf(Poll.Ready(Try.Ok(7))))
            }
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(TaskContext()))
        assertEquals(listOf("call:null:1", "future"), trace)
    }

    @Test
    fun nullableReturnedState() {
        val trace = mutableListOf<String>()
        val sink =
            unfold<Int?, Int, String?>(7) { state, item ->
                script(trace, state, item, listOf(Poll.Ready(Try.Ok(null))))
            }
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(SinkOutcome.Ready, sink.startSend(2))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollClose(TaskContext()))
        assertEquals(listOf("call:7:1", "future", "call:null:2", "future"), trace)
    }

    @Test
    fun callbackPanicConsumesState() {
        val trace = mutableListOf<String>()
        val sink =
            unfold<Int?, Int, String?>(7) { state, item ->
                trace.add("call:${state ?: "null"}:$item")
                error("callback panic")
            }
        assertFailsWith<IllegalStateException> { sink.startSend(1) }
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertFailsWith<IllegalStateException> { sink.startSend(2) }
        assertEquals(listOf("call:7:1"), trace)
    }

    @Test
    fun pendingFutureRetained() {
        val trace = mutableListOf<String>()
        val sink =
            unfold<Int?, Int, String?>(7) { state, item ->
                script(trace, state, item, listOf(Poll.Pending, Poll.Pending, Poll.Ready(Try.Ok(8))))
            }
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertSame(Poll.Pending, sink.pollReady(context))
        assertSame(Poll.Pending, sink.pollFlush(context))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(context))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(context))
        assertEquals(listOf("call:7:1", "future", "future", "future"), trace)
        assertEquals(2, wakes)
    }

    @Test
    fun nullableErrorEmptiesState() {
        val trace = mutableListOf<String>()
        val sink =
            unfold<Int?, Int, String?>(7) { state, item ->
                script(trace, state, item, listOf(Poll.Ready(Try.Err(null))))
            }
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(Poll.Ready(SinkOutcome.Err(null)), sink.pollFlush(TaskContext()))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollClose(TaskContext()))
        assertFailsWith<IllegalStateException> { sink.startSend(2) }
        assertEquals(listOf("call:7:1", "future"), trace)
    }

    @Test
    fun nonnullAccumulationControl() {
        val trace = mutableListOf<String>()
        val sink =
            unfold<Int?, Int, String?>(0) { state, item ->
                script(trace, state, item, listOf(Poll.Ready(Try.Ok(requireNotNull(state) + item))))
            }
        assertEquals(SinkOutcome.Ready, sink.startSend(2))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(SinkOutcome.Ready, sink.startSend(3))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollClose(TaskContext()))
        assertEquals(listOf("call:0:2", "future", "call:2:3", "future"), trace)
    }

    @Test
    fun overlappingSendPreservesFuture() {
        val trace = mutableListOf<String>()
        val sink =
            unfold<Int?, Int, String?>(7) { state, item ->
                script(trace, state, item, listOf(Poll.Ready(Try.Ok(8))))
            }
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertFailsWith<IllegalStateException> { sink.startSend(2) }
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(TaskContext()))
        assertEquals(listOf("call:7:1", "future"), trace)
    }

    @Test
    fun closePendingDrainsOnce() {
        val trace = mutableListOf<String>()
        val sink =
            unfold<Int?, Int, String?>(7) { state, item ->
                script(trace, state, item, listOf(Poll.Pending, Poll.Ready(Try.Ok(8))))
            }
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertSame(Poll.Pending, sink.pollClose(context))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollClose(context))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollClose(context))
        assertEquals(listOf("call:7:1", "future", "future"), trace)
        assertEquals(1, wakes)
    }
}
