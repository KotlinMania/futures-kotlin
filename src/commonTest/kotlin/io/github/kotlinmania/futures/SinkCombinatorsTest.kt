package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SinkCombinatorsTest {
    @Test
    fun testDrainSink() {
        val drain = drainSink<String>()
        val ctx = TaskContext()

        assertEquals(Poll.Ready(SinkOutcome.Ready), drain.pollReady(ctx))
        assertEquals(SinkOutcome.Ready, drain.startSend("hello"))
        assertEquals(Poll.Ready(SinkOutcome.Ready), drain.pollFlush(ctx))
        assertEquals(Poll.Ready(SinkOutcome.Ready), drain.pollClose(ctx))
    }

    @Test
    fun testSendFuture() {
        val list = mutableListOf<Int>()
        val sink = list.asSink()
        val future = sink.send(42)

        val pollRes = future.poll(TaskContext())
        assertEquals(Poll.Ready(Try.ok(Unit)), pollRes)
        assertEquals(listOf(42), list)
    }

    @Test
    fun testFeedFuture() {
        val list = mutableListOf<Int>()
        val sink = list.asSink()
        val future = sink.feed(100)

        val pollRes = future.poll(TaskContext())
        assertEquals(Poll.Ready(Try.ok(Unit)), pollRes)
        assertEquals(listOf(100), list)
    }

    @Test
    fun testFlushAndCloseFutures() {
        val list = mutableListOf<Int>()
        val sink = list.asSink()

        val flushFut = sink.flush()
        assertEquals(Poll.Ready(Try.ok(Unit)), flushFut.poll(TaskContext()))

        val closeFut = sink.close()
        assertEquals(Poll.Ready(Try.ok(Unit)), closeFut.poll(TaskContext()))
    }

    @Test
    fun testBufferSink() {
        val backing = mutableListOf<Int>()
        val sink = backing.asSink().buffer(3)
        val ctx = TaskContext()

        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(ctx))
        assertEquals(SinkOutcome.Ready, sink.startSend(1))
        assertEquals(SinkOutcome.Ready, sink.startSend(2))
        assertEquals(SinkOutcome.Ready, sink.startSend(3))

        // Buffer is full (size 3 >= capacity 3), but pollReady attempts to empty buffer into backing
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(ctx))
        // Backing should now have the drained items
        assertEquals(listOf(1, 2, 3), backing)
    }

    @Test
    fun testFanoutSink() {
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()
        val fanout = list1.asSink().fanout(list2.asSink())
        val ctx = TaskContext()

        assertEquals(Poll.Ready(SinkOutcome.Ready), fanout.pollReady(ctx))
        assertEquals(SinkOutcome.Ready, fanout.startSend("item1"))
        assertEquals(Poll.Ready(SinkOutcome.Ready), fanout.pollFlush(ctx))

        assertEquals(listOf("item1"), list1)
        assertEquals(listOf("item1"), list2)
    }

    @Test
    fun testSinkMapErr() {
        val failingSink = object : Sink<Int, String> {
            override fun pollReady(context: TaskContext): Poll<SinkOutcome<String>> =
                Poll.ready(SinkOutcome.err("fail-ready"))

            override fun startSend(item: Int): SinkOutcome<String> =
                SinkOutcome.err("fail-send")

            override fun pollFlush(context: TaskContext): Poll<SinkOutcome<String>> =
                Poll.ready(SinkOutcome.err("fail-flush"))

            override fun pollClose(context: TaskContext): Poll<SinkOutcome<String>> =
                Poll.ready(SinkOutcome.err("fail-close"))
        }

        val mapped = failingSink.sinkMapErr { "mapped:$it" }
        val ctx = TaskContext()

        assertEquals(Poll.Ready(SinkOutcome.Err("mapped:fail-ready")), mapped.pollReady(ctx))
        assertEquals(SinkOutcome.Err("mapped:fail-send"), mapped.startSend(1))
        assertEquals(Poll.Ready(SinkOutcome.Err("mapped:fail-flush")), mapped.pollFlush(ctx))
        assertEquals(Poll.Ready(SinkOutcome.Err("mapped:fail-close")), mapped.pollClose(ctx))
    }

    @Test
    fun testWithSink() {
        val list = mutableListOf<Int>()
        val sink = list.asSink().with { str: String ->
            ready(str.length)
        }
        val ctx = TaskContext()

        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollReady(ctx))
        assertEquals(SinkOutcome.Ready, sink.startSend("hello")) // length 5
        assertEquals(Poll.Ready(SinkOutcome.Ready), sink.pollFlush(ctx))

        assertEquals(listOf(5), list)
    }
}
