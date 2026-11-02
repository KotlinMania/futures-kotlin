package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StreamChunkPollingParityTest {
    private class ScriptStream(
        events: List<Poll<Yield<Int?>>>,
    ) : FusedStream<Int?> {
        private val events = events.toMutableList()
        private var ended = false
        var polls = 0
            private set

        override fun isTerminated(): Boolean = ended

        override fun pollNext(context: TaskContext): Poll<Yield<Int?>> {
            check(!ended) { "fixture polled after end" }
            polls += 1
            val event = events.removeAt(0)
            if (event is Poll.Ready && event.value is Yield.End) ended = true
            if (event is Poll.Pending) context.wakeByRef()
            return event
        }

        override fun sizeHint(): SizeHint {
            val count = events.count { it is Poll.Ready && it.value is Yield.Value }
            return SizeHint(count, count)
        }
    }

    private fun value(value: Int?): Poll<Yield<Int?>> = Poll.Ready(Yield.Value(value))

    private fun chunk(poll: Poll<Yield<List<Int?>>>): List<Int?> =
        assertIs<Yield.Value<List<Int?>>>(assertIs<Poll.Ready<Yield<List<Int?>>>>(poll).value).value

    @Test
    fun chunksFuseAfterTheFinalPartialChunk() {
        val input = ScriptStream(listOf(value(null), Poll.Ready(Yield.End)))
        val stream = input.chunks(4)
        assertSame(input, stream.getRef())
        assertEquals(listOf(null), chunk(stream.pollNext(TaskContext())))
        assertTrue(stream.isTerminated())
        repeat(2) {
            assertSame(Yield.End, assertIs<Poll.Ready<Yield<List<Int?>>>>(stream.pollNext(TaskContext())).value)
        }
        assertEquals(SizeHint(0, 0), stream.sizeHint())
        assertEquals(2, input.polls)
        assertSame(input, stream.intoInner())
    }

    @Test
    fun readyChunksFuseAfterTheFinalPartialChunk() {
        val input = ScriptStream(listOf(value(null), Poll.Ready(Yield.End)))
        val stream = input.readyChunks(4)
        assertSame(input, stream.getRef())
        assertEquals(listOf(null), chunk(stream.pollNext(TaskContext())))
        assertTrue(stream.isTerminated())
        repeat(2) {
            assertSame(Yield.End, assertIs<Poll.Ready<Yield<List<Int?>>>>(stream.pollNext(TaskContext())).value)
        }
        assertEquals(SizeHint(0, 0), stream.sizeHint())
        assertEquals(2, input.polls)
        assertSame(input, stream.intoInner())
    }

    @Test
    fun chunksKeepTheUpstreamConservativeUpperBound() {
        val input = ScriptStream((0..4).map { value(it) } + Poll.Ready(Yield.End))
        assertEquals(SizeHint(1, 5), input.chunks(4).sizeHint())
    }

    @Test
    fun chunksRetainNullableBufferedItemsAcrossPending() {
        val input = ScriptStream(listOf(value(null), Poll.Pending, value(2), value(3), Poll.Ready(Yield.End)))
        val stream = input.chunks(4)
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertEquals(SizeHint(1, 3), stream.sizeHint())
        assertEquals(listOf(null, 2, 3), chunk(stream.pollNext(TaskContext())))
        assertTrue(stream.isTerminated())
    }

    @Test
    fun readyChunksFlushOnPendingWithoutLosingNull() {
        val input = ScriptStream(listOf(value(null), Poll.Pending, value(2), Poll.Ready(Yield.End)))
        val stream = input.readyChunks(4)
        assertEquals(listOf(null), chunk(stream.pollNext(TaskContext())))
        assertEquals(SizeHint(0, 1), stream.sizeHint())
        assertEquals(listOf(2), chunk(stream.pollNext(TaskContext())))
    }

    @Test
    fun fullChunksKeepItemOrderAndNullPayloads() {
        val input = ScriptStream(listOf(value(1), value(null), value(3), Poll.Ready(Yield.End)))
        val stream = input.chunks(2)
        assertEquals(listOf(1, null), chunk(stream.pollNext(TaskContext())))
        assertEquals(listOf(3), chunk(stream.pollNext(TaskContext())))
    }
}
