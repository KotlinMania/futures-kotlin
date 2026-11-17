package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.sink.fanout
import kotlin.test.Test
import kotlin.test.assertEquals

class FanoutOrderParityTest {
    private enum class Operation(
        val label: String,
    ) {
        READY("ready"),
        FLUSH("flush"),
        CLOSE("close"),
    }

    private class Output(
        private val name: String,
        private val result: Poll<SinkOutcome<String?>>,
        private val trace: MutableList<String>,
    ) : Sink<Int, String?> {
        private fun event(operation: Operation, context: TaskContext): Poll<SinkOutcome<String?>> {
            trace.add("$name.${operation.label}")
            if (result === Poll.Pending) context.wakeByRef()
            return result
        }

        override fun pollReady(context: TaskContext): Poll<SinkOutcome<String?>> = event(Operation.READY, context)

        override fun startSend(item: Int): SinkOutcome<String?> = error("unexpected send")

        override fun pollFlush(context: TaskContext): Poll<SinkOutcome<String?>> = event(Operation.FLUSH, context)

        override fun pollClose(context: TaskContext): Poll<SinkOutcome<String?>> = event(Operation.CLOSE, context)
    }

    private fun check(
        operation: Operation,
        left: Poll<SinkOutcome<String?>>,
        right: Poll<SinkOutcome<String?>>,
        expected: Poll<SinkOutcome<String?>>,
        rightPolled: Boolean,
        expectedWakes: Int,
    ) {
        val trace = mutableListOf<String>()
        val sink = Output("left", left, trace).fanout(Output("right", right, trace))
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        val result =
            when (operation) {
                Operation.READY -> sink.pollReady(context)
                Operation.FLUSH -> sink.pollFlush(context)
                Operation.CLOSE -> sink.pollClose(context)
            }
        assertEquals(expected, result)
        val want = mutableListOf("left.${operation.label}")
        if (rightPolled) want.add("right.${operation.label}")
        assertEquals(want, trace)
        assertEquals(expectedWakes, wakes)
    }

    private fun leftError(operation: Operation) {
        check(
            operation,
            Poll.Ready(SinkOutcome.Err("left")),
            Poll.Ready(SinkOutcome.Err("right")),
            Poll.Ready(SinkOutcome.Err("left")),
            rightPolled = false,
            expectedWakes = 0,
        )
    }

    private fun nullLeftError(operation: Operation) {
        check(
            operation,
            Poll.Ready(SinkOutcome.Err(null)),
            Poll.Pending,
            Poll.Ready(SinkOutcome.Err(null)),
            rightPolled = false,
            expectedWakes = 0,
        )
    }

    private fun controls(operation: Operation) {
        val ok: Poll<SinkOutcome<String?>> = Poll.Ready(SinkOutcome.Ready)
        val pending: Poll<SinkOutcome<String?>> = Poll.Pending
        val err: Poll<SinkOutcome<String?>> = Poll.Ready(SinkOutcome.Err("right"))
        check(operation, ok, ok, ok, rightPolled = true, expectedWakes = 0)
        check(operation, ok, pending, pending, rightPolled = true, expectedWakes = 1)
        check(operation, pending, ok, pending, rightPolled = true, expectedWakes = 1)
        check(operation, pending, pending, pending, rightPolled = true, expectedWakes = 2)
        check(operation, pending, err, err, rightPolled = true, expectedWakes = 1)
        check(operation, ok, err, err, rightPolled = true, expectedWakes = 0)
    }

    @Test
    fun readyLeftError() = leftError(Operation.READY)

    @Test
    fun flushLeftError() = leftError(Operation.FLUSH)

    @Test
    fun closeLeftError() = leftError(Operation.CLOSE)

    @Test
    fun readyNullLeftError() = nullLeftError(Operation.READY)

    @Test
    fun flushNullLeftError() = nullLeftError(Operation.FLUSH)

    @Test
    fun closeNullLeftError() = nullLeftError(Operation.CLOSE)

    @Test
    fun readyPendingAndRightErrorControls() = controls(Operation.READY)

    @Test
    fun flushPendingAndRightErrorControls() = controls(Operation.FLUSH)

    @Test
    fun closePendingAndRightErrorControls() = controls(Operation.CLOSE)
}
