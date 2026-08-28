// port-lint: tests futures-util/tests/sink_unfold.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SinkUnfoldTest {
    private val context = TaskContext()

    @Test
    fun testSinkUnfoldAccumulates() {
        var recordedSum = 0
        val sink = unfoldSink<Int, Int, String>(0) { sum, item ->
            val next = sum + item
            recordedSum = next
            ok(next)
        }

        val ready1 = sink.pollReady(context)
        assertEquals(Poll.Ready(SinkOutcome.Ready), ready1)

        val send1 = sink.startSend(5)
        assertEquals(SinkOutcome.Ready, send1)

        val flush1 = sink.pollFlush(context)
        assertEquals(Poll.Ready(SinkOutcome.Ready), flush1)
        assertEquals(5, recordedSum)

        val ready2 = sink.pollReady(context)
        assertEquals(Poll.Ready(SinkOutcome.Ready), ready2)

        val send2 = sink.startSend(10)
        assertEquals(SinkOutcome.Ready, send2)

        val flush2 = sink.pollFlush(context)
        assertEquals(Poll.Ready(SinkOutcome.Ready), flush2)
        assertEquals(15, recordedSum)
    }

    @Test
    fun testSinkUnfoldError() {
        val sink = unfoldSink<Int, Int, String>(0) { _, item ->
            if (item < 0) {
                err("negative number")
            } else {
                ok(item)
            }
        }

        sink.pollReady(context)
        sink.startSend(-1)

        val flushRes = sink.pollFlush(context)
        assertEquals(Poll.Ready(SinkOutcome.Err("negative number")), flushRes)
    }
}
