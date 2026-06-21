package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SinkTest {
    /**
     * A small synchronous sink with a bounded buffer, used to exercise the
     * full poll/send/flush/close lifecycle including back-pressure and
     * error surfaces.
     */
    private class BoundedSink(
        private val capacity: Int,
    ) : Sink<Int, String> {
        val received: MutableList<Int> = mutableListOf()
        var pendingFlush: Boolean = false
        var closed: Boolean = false
        var refuseSend: String? = null

        override fun pollReady(context: TaskContext): Poll<SinkOutcome<String>> =
            when {
                closed -> Poll.ready(SinkOutcome.err("closed"))
                received.size >= capacity -> {
                    context.wakeByRef()
                    Poll.pending()
                }
                else -> Poll.ready(SinkOutcome.ready())
            }

        override fun startSend(item: Int): SinkOutcome<String> {
            val refusal = refuseSend
            if (refusal != null) return SinkOutcome.err(refusal)
            received.add(item)
            pendingFlush = true
            return SinkOutcome.ready()
        }

        override fun pollFlush(context: TaskContext): Poll<SinkOutcome<String>> =
            if (pendingFlush) {
                pendingFlush = false
                Poll.ready(SinkOutcome.ready())
            } else {
                Poll.ready(SinkOutcome.ready())
            }

        override fun pollClose(context: TaskContext): Poll<SinkOutcome<String>> {
            closed = true
            return Poll.ready(SinkOutcome.ready())
        }
    }

    @Test
    fun pollReadyAcceptsThenBackPressuresAtCapacity() {
        val sink = BoundedSink(capacity = 2)
        var woke = false
        val context = TaskContext(Waker { woke = true })

        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(context))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))

        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(context))
        assertEquals(SinkOutcome.Ready, sink.startSend(2))

        // At capacity, pollReady reports pending and registers the waker.
        assertEquals(Poll.Pending, sink.pollReady(context))
        assertTrue(woke, "pollReady at capacity must wake the registered task")
    }

    @Test
    fun startSendSurfacesErrorPayload() {
        val sink = BoundedSink(capacity = 4)
        sink.refuseSend = "no-space"

        val outcome = sink.startSend(42)
        assertEquals(SinkOutcome.Err("no-space"), outcome)
        assertTrue(sink.received.isEmpty())
    }

    @Test
    fun pollCloseTransitionsToFailedSendAfterClose() {
        val sink = BoundedSink(capacity = 4)

        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(TaskContext()))
        assertEquals(SinkOutcome.Ready, sink.startSend(7))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollClose(TaskContext()))
        assertTrue(sink.closed)

        // After close, pollReady reports the documented error path.
        assertEquals(Poll.Ready(SinkOutcome.Err("closed")), sink.pollReady(TaskContext()))
    }

    @Test
    fun pollFlushDrainsPendingSend() {
        val sink = BoundedSink(capacity = 4)
        assertEquals(SinkOutcome.Ready, sink.startSend(99))
        assertTrue(sink.pendingFlush)
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(TaskContext()))
        assertEquals(false, sink.pendingFlush)
    }

    @Test
    fun mutableListAsSinkAppendsInOrder() {
        val backing: MutableList<String> = mutableListOf()
        val sink: Sink<String, Nothing> = backing.asSink()
        val ctx = TaskContext()

        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(ctx))
        assertEquals(SinkOutcome.Ready, sink.startSend("a"))
        assertEquals(SinkOutcome.Ready, sink.startSend("b"))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(ctx))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollClose(ctx))

        assertEquals(listOf("a", "b"), backing)
    }

    @Test
    fun arrayDequeAsSinkAppendsToBack() {
        val backing: ArrayDeque<Int> = ArrayDeque()
        backing.addFirst(0)
        val sink: Sink<Int, Nothing> = backing.asSink()
        val ctx = TaskContext()

        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(SinkOutcome.Ready, sink.startSend(2))
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollClose(ctx))

        // The pre-existing front element is preserved; new items land at the back.
        assertEquals(listOf(0, 1, 2), backing.toList())
    }

    @Test
    fun foldRoutesEachVariant() {
        val ready: SinkOutcome<String> = SinkOutcome.Ready
        val err: SinkOutcome<String> = SinkOutcome.Err("boom")

        assertEquals("ok", ready.fold(onReady = { "ok" }, onErr = { "fail:$it" }))
        assertEquals("fail:boom", err.fold(onReady = { "ok" }, onErr = { "fail:$it" }))
    }

    @Test
    fun errorOrNullExposesErrorPayload() {
        val ready: SinkOutcome<String> = SinkOutcome.Ready
        val err: SinkOutcome<String> = SinkOutcome.Err("nope")

        assertNull(ready.errorOrNull())
        assertEquals("nope", err.errorOrNull())
    }

    @Test
    fun sinkOutcomeReadySingleton() {
        // The Ready data object is a singleton — both accesses return the
        // same instance and equal under `==`.
        val a: SinkOutcome<Int> = SinkOutcome.Ready
        val b: SinkOutcome<String> = SinkOutcome.ready()
        assertSame(SinkOutcome.Ready, a)
        assertSame(SinkOutcome.Ready, b)
    }
}
