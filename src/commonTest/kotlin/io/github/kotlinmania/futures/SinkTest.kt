// port-lint: tests futures/tests/sink.rs
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

    @Test
    fun eitherSink() {
        val list = mutableListOf<Int>()
        val deque = ArrayDeque<Int>()
        val s: Sink<Int, Nothing> = if (true) {
            list.asSink().leftSink<Int, Nothing, Sink<Int, Nothing>>().asSink()
        } else {
            deque.asSink().rightSink<Int, Nothing, Sink<Int, Nothing>>().asSink()
        }
        assertEquals(SinkOutcome.Ready, s.startSend(0))
        assertEquals(listOf(0), list)
    }

    @Test
    fun vecSink() {
        val v = mutableListOf<Int>()
        val sink = v.asSink()
        assertEquals(SinkOutcome.Ready, sink.startSend(0))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(listOf(0, 1), v)
        val flushFut = sink.flush()
        val pollRes = flushFut.poll(TaskContext())
        assertEquals(Poll.Ready(Try.Ok(Unit)), pollRes)
        assertEquals(listOf(0, 1), v)
    }

    @Test
    fun vecdequeSink() {
        val deque = ArrayDeque<Int>()
        val sink = deque.asSink()
        assertEquals(SinkOutcome.Ready, sink.startSend(2))
        assertEquals(SinkOutcome.Ready, sink.startSend(3))
        assertEquals(2, deque.removeFirstOrNull())
        assertEquals(3, deque.removeFirstOrNull())
        assertEquals(null, deque.removeFirstOrNull())
    }

    @Test
    fun send() {
        val v = mutableListOf<Int>()
        val sink = v.asSink()

        val fut0 = sink.send(0)
        assertEquals(Poll.Ready(Try.Ok(Unit)), fut0.poll(TaskContext()))
        assertEquals(listOf(0), v)

        val fut1 = sink.send(1)
        assertEquals(Poll.Ready(Try.Ok(Unit)), fut1.poll(TaskContext()))
        assertEquals(listOf(0, 1), v)

        val fut2 = sink.send(2)
        assertEquals(Poll.Ready(Try.Ok(Unit)), fut2.poll(TaskContext()))
        assertEquals(listOf(0, 1, 2), v)
    }

    @Test
    fun sendAll() {
        val v = mutableListOf<Int>()
        val sink = v.asSink()

        val st1 = streamIter(listOf(Try.Ok(0), Try.Ok(1)))
        val fut1 = sink.sendAll(st1)
        assertEquals(Poll.Ready(Try.Ok(Unit)), fut1.poll(TaskContext()))
        assertEquals(listOf(0, 1), v)

        val st2 = streamIter(listOf(Try.Ok(2), Try.Ok(3)))
        val fut2 = sink.sendAll(st2)
        assertEquals(Poll.Ready(Try.Ok(Unit)), fut2.poll(TaskContext()))
        assertEquals(listOf(0, 1, 2, 3), v)

        val st3 = streamIter(listOf(Try.Ok(4), Try.Ok(5)))
        val fut3 = sink.sendAll(st3)
        assertEquals(Poll.Ready(Try.Ok(Unit)), fut3.poll(TaskContext()))
        assertEquals(listOf(0, 1, 2, 3, 4, 5), v)
    }

    @Test
    fun withAsMap() {
        val v = mutableListOf<Int>()
        val sink = v.asSink().with<Int, Int, Nothing> { item ->
            ready(item * 2)
        }
        assertEquals(Poll.Ready(Try.Ok(Unit)), sink.send(0).poll(TaskContext()))
        assertEquals(Poll.Ready(Try.Ok(Unit)), sink.send(1).poll(TaskContext()))
        assertEquals(Poll.Ready(Try.Ok(Unit)), sink.send(2).poll(TaskContext()))
        assertEquals(listOf(0, 2, 4), v)
    }

    @Test
    fun withFlatMap() {
        val v = mutableListOf<Int>()
        val sink = v.asSink().withFlatMap<Int, Int, Nothing> { item ->
            val items = List(item) { item }
            streamIter(items.map { Try.Ok(it) })
        }
        assertEquals(Poll.Ready(Try.Ok(Unit)), sink.send(0).poll(TaskContext()))
        assertEquals(Poll.Ready(Try.Ok(Unit)), sink.send(1).poll(TaskContext()))
        assertEquals(Poll.Ready(Try.Ok(Unit)), sink.send(2).poll(TaskContext()))
        assertEquals(Poll.Ready(Try.Ok(Unit)), sink.send(3).poll(TaskContext()))
        assertEquals(listOf(1, 2, 2, 3, 3, 3), v)
    }

    @Test
    fun bufferNoop() {
        val v = mutableListOf<Int>()
        val sink0 = v.asSink().buffer(0)
        assertEquals(Poll.Ready(Try.Ok(Unit)), sink0.send(0).poll(TaskContext()))
        assertEquals(Poll.Ready(Try.Ok(Unit)), sink0.send(1).poll(TaskContext()))
        assertEquals(listOf(0, 1), v)

        val sink1 = v.asSink().buffer(1)
        assertEquals(Poll.Ready(Try.Ok(Unit)), sink1.send(2).poll(TaskContext()))
        assertEquals(Poll.Ready(Try.Ok(Unit)), sink1.send(3).poll(TaskContext()))
        assertEquals(listOf(0, 1, 2, 3), v)
    }

    @Test
    fun fanoutSmoke() {
        val sink1 = mutableListOf<Int>()
        val sink2 = mutableListOf<Int>()
        val fanout = sink1.asSink().fanout(sink2.asSink())
        val st = streamIter(listOf(Try.Ok(1), Try.Ok(2), Try.Ok(3)))
        assertEquals(Poll.Ready(Try.Ok(Unit)), fanout.sendAll(st).poll(TaskContext()))
        assertEquals(listOf(1, 2, 3), sink1)
        assertEquals(listOf(1, 2, 3), sink2)
    }

    @Test
    fun sinkMapErr() {
        val bounded = BoundedSink(capacity = 0)
        bounded.refuseSend = "failed"
        val mapped = bounded.sinkMapErr { it.length }
        assertEquals(SinkOutcome.Err(6), mapped.startSend(1))
    }

    @Test
    fun errInto() {
        val bounded = BoundedSink(capacity = 0)
        bounded.refuseSend = "error"
        val converted = bounded.sinkErrInto { "wrapped:$it" }
        assertEquals(SinkOutcome.Err("wrapped:error"), converted.startSend(1))
    }
}

