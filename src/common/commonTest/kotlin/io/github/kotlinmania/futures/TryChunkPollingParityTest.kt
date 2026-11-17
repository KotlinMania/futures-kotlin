package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TryChunkPollingParityTest {
    private class ScriptStream(
        events: List<Poll<Yield<Try<Int?, String?>>>>,
    ) : TryStream<Int?, String?>,
        FusedStream<Try<Int?, String?>> {
        private val events = events.toMutableList()
        private var ended = false
        var polls = 0
            private set

        override fun isTerminated(): Boolean = ended

        override fun pollNext(context: TaskContext): Poll<Yield<Try<Int?, String?>>> {
            check(!ended) { "fixture polled after end" }
            polls += 1
            val event = events.removeAt(0)
            if (event == Poll.Ready(Yield.End)) ended = true
            if (event is Poll.Pending) context.wakeByRef()
            return event
        }

        override fun sizeHint(): SizeHint {
            val count = events.count { it is Poll.Ready && it.value is Yield.Value }
            return SizeHint(count, count)
        }
    }

    private fun okValue(value: Int?): Poll<Yield<Try<Int?, String?>>> = Poll.Ready(Yield.Value(Try.Ok(value)))

    private fun errorValue(error: String?): Poll<Yield<Try<Int?, String?>>> = Poll.Ready(Yield.Value(Try.Err(error)))

    private fun <E> item(poll: Poll<Yield<Try<List<Int?>, E>>>): Try<List<Int?>, E> =
        assertIs<Yield.Value<Try<List<Int?>, E>>>(assertIs<Poll.Ready<Yield<Try<List<Int?>, E>>>>(poll).value).value

    @Test
    fun tryChunksFuseAfterFinalPartialChunk() {
        val input = ScriptStream(listOf(okValue(null), Poll.Ready(Yield.End)))
        val stream = input.tryChunks(4)
        assertEquals(listOf(null), assertIs<Try.Ok<List<Int?>>>(item(stream.pollNext(TaskContext()))).value)
        assertTrue(stream.isTerminated())
        repeat(2) {
            assertSame(Yield.End, assertIs<Poll.Ready<Yield<Try<List<Int?>, TryChunksError<Int?, String?>>>>>(stream.pollNext(TaskContext())).value)
        }
        assertEquals(SizeHint(0, 0), stream.sizeHint())
        assertEquals(2, input.polls)
    }

    @Test
    fun tryReadyChunksFuseAfterFinalPartialChunk() {
        val input = ScriptStream(listOf(okValue(null), Poll.Ready(Yield.End)))
        val stream = input.tryReadyChunks(4)
        assertEquals(listOf(null), assertIs<Try.Ok<List<Int?>>>(item(stream.pollNext(TaskContext()))).value)
        assertTrue(stream.isTerminated())
        repeat(2) {
            assertSame(Yield.End, assertIs<Poll.Ready<Yield<Try<List<Int?>, TryReadyChunksError<Int?, String?>>>>>(stream.pollNext(TaskContext())).value)
        }
        assertEquals(SizeHint(0, 0), stream.sizeHint())
        assertEquals(2, input.polls)
    }

    @Test
    fun tryChunksUpperBoundIncludesEveryError() {
        val stream = ScriptStream(listOf(errorValue("a"), errorValue(null), errorValue("c"), Poll.Ready(Yield.End))).tryChunks(4)
        assertEquals(SizeHint(0, 3), stream.sizeHint())
        repeat(3) {
            assertIs<Try.Err<TryChunksError<Int?, String?>>>(item(stream.pollNext(TaskContext())))
        }
    }

    @Test
    fun tryReadyChunksUpperBoundIncludesEveryError() {
        val stream = ScriptStream(listOf(errorValue("a"), errorValue(null), errorValue("c"), Poll.Ready(Yield.End))).tryReadyChunks(4)
        assertEquals(SizeHint(0, 3), stream.sizeHint())
        repeat(3) {
            assertIs<Try.Err<TryReadyChunksError<Int?, String?>>>(item(stream.pollNext(TaskContext())))
        }
    }

    @Test
    fun tryChunksPreserveBufferAcrossPendingAndReturnItWithNullableError() {
        val stream = ScriptStream(listOf(okValue(null), Poll.Pending, errorValue(null), okValue(2), Poll.Ready(Yield.End))).tryChunks(4)
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertEquals(SizeHint(1, 3), stream.sizeHint())
        val error = assertIs<Try.Err<TryChunksError<Int?, String?>>>(item(stream.pollNext(TaskContext()))).error
        assertEquals(listOf(null), error.items)
        assertNull(error.error)
        assertFalse(stream.isTerminated())
        assertEquals(listOf(2), assertIs<Try.Ok<List<Int?>>>(item(stream.pollNext(TaskContext()))).value)
        assertTrue(stream.isTerminated())
    }

    @Test
    fun tryReadyChunksReturnBufferedItemsWithErrorAndResume() {
        val stream = ScriptStream(listOf(okValue(null), errorValue(null), okValue(2), Poll.Ready(Yield.End))).tryReadyChunks(4)
        val error = assertIs<Try.Err<TryReadyChunksError<Int?, String?>>>(item(stream.pollNext(TaskContext()))).error
        assertEquals(listOf(null), error.items)
        assertNull(error.error)
        assertFalse(stream.isTerminated())
        assertEquals(listOf(2), assertIs<Try.Ok<List<Int?>>>(item(stream.pollNext(TaskContext()))).value)
    }

    @Test
    fun tryReadyChunksFlushOnPendingBeforeCollectingTheNextError() {
        val stream = ScriptStream(listOf(okValue(null), Poll.Pending, errorValue("later"), Poll.Ready(Yield.End))).tryReadyChunks(4)
        assertEquals(listOf(null), assertIs<Try.Ok<List<Int?>>>(item(stream.pollNext(TaskContext()))).value)
        val error = assertIs<Try.Err<TryReadyChunksError<Int?, String?>>>(item(stream.pollNext(TaskContext()))).error
        assertTrue(error.items.isEmpty())
        assertEquals("later", error.error)
    }
}
