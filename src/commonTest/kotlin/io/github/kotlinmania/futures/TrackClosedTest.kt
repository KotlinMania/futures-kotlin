package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.Cursor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackClosedTest {
    @Test
    fun testTrackClosedWrite() {
        val cursor = Cursor()
        val tracked = cursor.trackClosed()
        val cx = TaskContext()

        assertFalse(tracked.isClosed())
        val writePoll = tracked.pollWrite(cx, byteArrayOf(1, 2, 3), 0, 3)
        assertTrue(writePoll.isReady())

        val closePoll = tracked.pollClose(cx)
        assertTrue(closePoll.isReady())
        assertTrue(tracked.isClosed())

        val writeAfterClose = tracked.pollWrite(cx, byteArrayOf(4), 0, 1)
        assertTrue(writeAfterClose.isReady())
        assertTrue((writeAfterClose as Poll.Ready).value is Try.Err)
    }

    @Test
    fun testTrackClosedSink() {
        val sink =
            object : Sink<Int, String> {
                override fun pollReady(context: TaskContext): Poll<SinkOutcome<String>> =
                    Poll.Ready(SinkOutcome.ready())

                override fun startSend(item: Int): SinkOutcome<String> = SinkOutcome.ready()

                override fun pollFlush(context: TaskContext): Poll<SinkOutcome<String>> =
                    Poll.Ready(SinkOutcome.ready())

                override fun pollClose(context: TaskContext): Poll<SinkOutcome<String>> =
                    Poll.Ready(SinkOutcome.ready())
            }

        val tracked = sink.trackClosed()
        val cx = TaskContext()

        assertFalse(tracked.isClosed())
        assertTrue(tracked.pollReady(cx).isReady())
        assertEquals(SinkOutcome.ready(), tracked.startSend(42))
        assertTrue(tracked.pollFlush(cx).isReady())

        assertTrue(tracked.pollClose(cx).isReady())
        assertTrue(tracked.isClosed())

        assertFailsWith<IllegalStateException> {
            tracked.startSend(99)
        }
    }
}
