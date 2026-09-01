// port-lint: tests futures-util/tests/io_into_sink.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.Cursor
import io.github.kotlinmania.futures.io.intoSink
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class IntoSinkTest {
    private val context = TaskContext()

    @Test
    fun testIntoSinkSendAndFlush() {
        val cursor = Cursor(ByteArray(20))
        val sink = cursor.intoSink()

        val ready1 = sink.pollReady(context)
        assertEquals(Poll.Ready(SinkOutcome.Ready), ready1)

        val sendRes1 = sink.startSend(byteArrayOf(1, 2, 3))
        assertEquals(SinkOutcome.Ready, sendRes1)

        val flushRes1 = sink.pollFlush(context)
        assertEquals(Poll.Ready(SinkOutcome.Ready), flushRes1)

        assertEquals(3L, cursor.position())
        assertContentEquals(byteArrayOf(1, 2, 3), cursor.getRef().copyOfRange(0, 3))

        val ready2 = sink.pollReady(context)
        assertEquals(Poll.Ready(SinkOutcome.Ready), ready2)

        val sendRes2 = sink.startSend(byteArrayOf(4, 5))
        assertEquals(SinkOutcome.Ready, sendRes2)

        val closeRes = sink.pollClose(context)
        assertEquals(Poll.Ready(SinkOutcome.Ready), closeRes)

        assertEquals(5L, cursor.position())
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), cursor.getRef().copyOfRange(0, 5))
    }
}
