// port-lint: tests futures/tests/stream_split.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SplitTest {
    private class SimpleStreamSink :
        Stream<Int>,
        Sink<Int, String> {
        val streamItems = mutableListOf(10, 20, 30)
        val sinkItems = mutableListOf<Int>()

        override fun pollNext(context: TaskContext): Poll<Yield<Int>> =
            if (streamItems.isNotEmpty()) {
                Poll.ready(Yield.value(streamItems.removeAt(0)))
            } else {
                Poll.ready(Yield.end())
            }

        override fun pollReady(context: TaskContext): Poll<SinkOutcome<String>> = Poll.ready(SinkOutcome.ready())

        override fun startSend(item: Int): SinkOutcome<String> {
            sinkItems.add(item)
            return SinkOutcome.ready()
        }

        override fun pollFlush(context: TaskContext): Poll<SinkOutcome<String>> = Poll.ready(SinkOutcome.ready())

        override fun pollClose(context: TaskContext): Poll<SinkOutcome<String>> = Poll.ready(SinkOutcome.ready())
    }

    @Test
    fun testSplitAndReunite() {
        val join = SimpleStreamSink()
        val (sink, stream) = join.split()

        assertTrue(sink.isPairOf(stream))
        assertTrue(stream.isPairOf(sink))

        val reunited = sink.reunite(stream)
        assertTrue(reunited.isSuccess)
        val recovered = reunited.getOrThrow()
        assertEquals(3, recovered.streamItems.size)
    }

    @Test
    fun testSplitStreamAndSinkOperations() {
        val join = SimpleStreamSink()
        val (sink, stream) = join.split()
        val cx = TaskContext()

        // Poll stream items
        val p1 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(p1)
        assertEquals(10, (p1.value as Yield.Value).value)

        // Send to sink
        val ready = sink.pollReady(cx)
        assertIs<Poll.Ready<SinkOutcome<String>>>(ready)
        sink.startSend(99)

        val flush = sink.pollFlush(cx)
        assertIs<Poll.Ready<SinkOutcome<String>>>(flush)

        // Reunite
        val reunited = stream.reunite(sink).getOrThrow()
        assertEquals(listOf(99), reunited.sinkItems)
        assertEquals(listOf(20, 30), reunited.streamItems)
    }

    @Test
    fun testMismatchedReuniteFails() {
        val join1 = SimpleStreamSink()
        val join2 = SimpleStreamSink()
        val (sink1, _) = join1.split()
        val (_, stream2) = join2.split()

        assertFalse(sink1.isPairOf(stream2))
        val failReunite = sink1.reunite(stream2)
        assertTrue(failReunite.isFailure)
        assertIs<SplitReuniteError>(failReunite.exceptionOrNull())
    }

    private class NopStream<T> :
        Stream<T>,
        Sink<T, Unit> {
        override fun pollNext(context: TaskContext): Poll<Yield<T>> = Poll.pending()

        override fun pollReady(context: TaskContext): Poll<SinkOutcome<Unit>> = Poll.ready(SinkOutcome.ready())

        override fun startSend(item: T): SinkOutcome<Unit> = SinkOutcome.ready()

        override fun pollFlush(context: TaskContext): Poll<SinkOutcome<Unit>> = Poll.ready(SinkOutcome.ready())

        override fun pollClose(context: TaskContext): Poll<SinkOutcome<Unit>> = Poll.ready(SinkOutcome.ready())
    }

    @Test
    fun testPairing() {
        val s1 = NopStream<Unit>()
        val (sink1, stream1) = s1.split()
        assertTrue(sink1.isPairOf(stream1))
        assertTrue(stream1.isPairOf(sink1))

        val s2 = NopStream<Unit>()
        val (sink2, stream2) = s2.split()
        assertTrue(sink2.isPairOf(stream2))
        assertTrue(stream2.isPairOf(sink2))

        assertFalse(sink1.isPairOf(stream2))
        assertFalse(stream1.isPairOf(sink2))
    }
}
