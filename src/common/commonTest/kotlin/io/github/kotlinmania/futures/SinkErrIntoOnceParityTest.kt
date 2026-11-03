package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.sink.sinkErrInto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SinkErrIntoOnceParityTest {
    private enum class Operation(
        val label: String,
    ) {
        READY("ready"),
        SEND("send"),
        FLUSH("flush"),
        CLOSE("close"),
    }

    private class Output(
        val trace: MutableList<String>,
        events: List<Poll<SinkOutcome<String?>>>,
    ) : Sink<Int, String?> {
        private val events = events.toMutableList()

        private fun event(operation: Operation, context: TaskContext?): Poll<SinkOutcome<String?>> {
            trace.add(operation.label)
            check(events.isNotEmpty()) { "unexpected sink operation" }
            val result = events.removeAt(0)
            if (result === Poll.Pending) requireNotNull(context).wakeByRef()
            return result
        }

        override fun pollReady(context: TaskContext): Poll<SinkOutcome<String?>> = event(Operation.READY, context)

        override fun startSend(item: Int): SinkOutcome<String?> =
            when (val result = event(Operation.SEND, null)) {
                is Poll.Ready -> result.value
                Poll.Pending -> error("unexpected Pending send")
            }

        override fun pollFlush(context: TaskContext): Poll<SinkOutcome<String?>> = event(Operation.FLUSH, context)

        override fun pollClose(context: TaskContext): Poll<SinkOutcome<String?>> = event(Operation.CLOSE, context)
    }

    private fun call(
        sink: Sink<Int, String?>,
        operation: Operation,
        context: TaskContext,
    ): Poll<SinkOutcome<String?>> =
        when (operation) {
            Operation.READY -> sink.pollReady(context)
            Operation.SEND -> Poll.Ready(sink.startSend(1))
            Operation.FLUSH -> sink.pollFlush(context)
            Operation.CLOSE -> sink.pollClose(context)
        }

    private fun firstError(first: Operation) {
        for (second in Operation.entries) {
            val trace = mutableListOf<String>()
            val output =
                Output(
                    trace,
                    listOf(Poll.Ready(SinkOutcome.Err("first")), Poll.Ready(SinkOutcome.Err("second"))),
                )
            val sink =
                output.sinkErrInto {
                    trace.add("map:$it")
                    "mapped"
                }
            assertEquals(Poll.Ready(SinkOutcome.Err("mapped")), call(sink, first, TaskContext()))
            val error = assertFailsWith<IllegalStateException> { call(sink, second, TaskContext()) }
            assertEquals("polled MapErr after completion", error.message)
            assertEquals(listOf(first.label, "map:first", second.label), trace)
        }
    }

    @Test
    fun readyConsumesMapper() = firstError(Operation.READY)

    @Test
    fun sendConsumesMapper() = firstError(Operation.SEND)

    @Test
    fun flushConsumesMapper() = firstError(Operation.FLUSH)

    @Test
    fun closeConsumesMapper() = firstError(Operation.CLOSE)

    @Test
    fun nullErrorAndNullResultAreErrors() {
        val trace = mutableListOf<String>()
        val output = Output(trace, listOf(Poll.Ready(SinkOutcome.Err(null)), Poll.Ready(SinkOutcome.Err("second"))))
        val sink =
            output.sinkErrInto<Int, String?, String?> {
                assertEquals(null, it)
                trace.add("map:null")
                null
            }
        assertEquals(Poll.Ready(SinkOutcome.Err(null)), call(sink, Operation.READY, TaskContext()))
        val error = assertFailsWith<IllegalStateException> { call(sink, Operation.CLOSE, TaskContext()) }
        assertEquals("polled MapErr after completion", error.message)
        assertEquals(listOf("ready", "map:null", "close"), trace)
    }

    @Test
    fun mapperPanicConsumesMapper() {
        val trace = mutableListOf<String>()
        val output = Output(trace, listOf(Poll.Ready(SinkOutcome.Err("first")), Poll.Ready(SinkOutcome.Err("second"))))
        val sink =
            output.sinkErrInto<Int, String?, String?> {
                trace.add("map:panic")
                error("mapper panic")
            }
        val first = assertFailsWith<IllegalStateException> { call(sink, Operation.FLUSH, TaskContext()) }
        assertEquals("mapper panic", first.message)
        val second = assertFailsWith<IllegalStateException> { call(sink, Operation.SEND, TaskContext()) }
        assertEquals("polled MapErr after completion", second.message)
        assertEquals(listOf("flush", "map:panic", "send"), trace)
    }

    @Test
    fun pendingAndSuccessPreserveMapper() {
        val trace = mutableListOf<String>()
        val output =
            Output(
                trace,
                listOf(
                    Poll.Pending,
                    Poll.Ready(SinkOutcome.Ready),
                    Poll.Ready(SinkOutcome.Ready),
                    Poll.Ready(SinkOutcome.Ready),
                    Poll.Ready(SinkOutcome.Err("first")),
                ),
            )
        val sink =
            output.sinkErrInto {
                trace.add("map:$it")
                "mapped"
            }
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertSame(Poll.Pending, call(sink, Operation.READY, context))
        for (operation in listOf(Operation.FLUSH, Operation.CLOSE, Operation.SEND)) {
            assertEquals(Poll.Ready(SinkOutcome.Ready), call(sink, operation, context))
        }
        assertEquals(Poll.Ready(SinkOutcome.Err("mapped")), call(sink, Operation.READY, context))
        assertEquals(listOf("ready", "flush", "close", "send", "ready", "map:first"), trace)
        assertEquals(1, wakes)
    }

    @Test
    fun successAfterConsumptionStillDelegates() {
        val trace = mutableListOf<String>()
        val output =
            Output(
                trace,
                listOf(
                    Poll.Ready(SinkOutcome.Err("first")),
                    Poll.Ready(SinkOutcome.Ready),
                    Poll.Pending,
                    Poll.Ready(SinkOutcome.Err("second")),
                ),
            )
        val sink =
            output.sinkErrInto {
                trace.add("map:$it")
                "mapped"
            }
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertEquals(Poll.Ready(SinkOutcome.Err("mapped")), call(sink, Operation.READY, context))
        assertEquals(Poll.Ready(SinkOutcome.Ready), call(sink, Operation.FLUSH, context))
        assertSame(Poll.Pending, call(sink, Operation.CLOSE, context))
        val error = assertFailsWith<IllegalStateException> { call(sink, Operation.SEND, context) }
        assertEquals("polled MapErr after completion", error.message)
        assertEquals(listOf("ready", "map:first", "flush", "close", "send"), trace)
        assertEquals(1, wakes)
    }

    @Test
    fun getterAndDisplayPreserveOriginalSink() {
        val trace = mutableListOf<String>()
        val output = Output(trace, listOf(Poll.Ready(SinkOutcome.Ready)))
        val sink = output.sinkErrInto { it }
        assertSame(output, sink.getRef())
        assertEquals("SinkErrInto(sink=$output)", sink.toString())
        assertEquals(emptyList(), trace)
    }
}
