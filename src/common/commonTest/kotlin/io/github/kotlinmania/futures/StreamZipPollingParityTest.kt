package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StreamZipPollingParityTest {
    private class ScriptStream(
        private val id: Int,
        events: List<Poll<Yield<Int?>>>,
        private val trace: MutableList<Int>,
    ) : FusedStream<Int?> {
        private val events = events.toMutableList()
        private var ended = false

        override fun isTerminated(): Boolean = ended || events.firstOrNull() == Poll.Ready(Yield.End)

        override fun pollNext(context: TaskContext): Poll<Yield<Int?>> {
            check(!ended) { "fixture polled after end" }
            trace.add(id)
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

    private fun value(value: Int?): Poll<Yield<Int?>> = Poll.Ready(Yield.Value(value))

    @Test
    fun leftEndStillPollsAndQueuesRightBeforeReturningEnd() {
        val trace = mutableListOf<Int>()
        val left = ScriptStream(0, listOf(Poll.Ready(Yield.End)), trace)
        val right = ScriptStream(1, listOf(value(7), Poll.Ready(Yield.End)), trace)
        val stream = left.zip(right)
        assertSame(Yield.End, assertIs<Poll.Ready<Yield<Pair<Int?, Int?>>>>(stream.pollNext(TaskContext())).value)
        assertEquals(listOf(0, 1), trace)
        assertFalse(stream.isTerminated())
        assertEquals(SizeHint(0, 0), stream.sizeHint())
        assertSame(left, stream.getRef().first)
        assertSame(right, stream.intoInner().second)
    }

    @Test
    fun rightEndKeepsLeftPendingAndUsesFusedInputOnFurtherPoll() {
        val trace = mutableListOf<Int>()
        val left = ScriptStream(0, listOf(Poll.Pending, Poll.Pending), trace)
        val right = ScriptStream(1, listOf(Poll.Ready(Yield.End)), trace)
        val stream = left.zip(right)
        repeat(2) {
            assertSame(Yield.End, assertIs<Poll.Ready<Yield<Pair<Int?, Int?>>>>(stream.pollNext(TaskContext())).value)
        }
        assertEquals(listOf(0, 1, 0), trace)
        assertFalse(stream.isTerminated())
    }

    @Test
    fun bothEndedInputsAreObservedInTheSamePoll() {
        val trace = mutableListOf<Int>()
        val stream = ScriptStream(0, listOf(Poll.Ready(Yield.End)), trace).zip(ScriptStream(1, listOf(Poll.Ready(Yield.End)), trace))
        assertSame(Yield.End, assertIs<Poll.Ready<Yield<Pair<Int?, Int?>>>>(stream.pollNext(TaskContext())).value)
        assertEquals(listOf(0, 1), trace)
        assertTrue(stream.isTerminated())
    }

    @Test
    fun terminationTracksFuseObservationRatherThanRawInnerStatus() {
        val trace = mutableListOf<Int>()
        val stream = ScriptStream(0, listOf(Poll.Ready(Yield.End)), trace).zip(ScriptStream(1, listOf(Poll.Ready(Yield.End)), trace))
        assertFalse(stream.isTerminated())
        assertTrue(trace.isEmpty())
    }

    @Test
    fun nullableBufferedValueSurvivesPendingWithoutRepollingItsInput() {
        val trace = mutableListOf<Int>()
        val stream = ScriptStream(0, listOf(value(null), Poll.Ready(Yield.End)), trace).zip(ScriptStream(1, listOf(Poll.Pending, value(7), Poll.Ready(Yield.End)), trace))
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertEquals(SizeHint(1, 1), stream.sizeHint())
        val result = assertIs<Yield.Value<Pair<Int?, Int?>>>(assertIs<Poll.Ready<Yield<Pair<Int?, Int?>>>>(stream.pollNext(TaskContext())).value).value
        assertEquals(Pair(null, 7), result)
        assertEquals(listOf(0, 1, 1), trace)
    }

    @Test
    fun entirelyPendingPollVisitsBothInputs() {
        val trace = mutableListOf<Int>()
        val stream = ScriptStream(0, listOf(Poll.Pending), trace).zip(ScriptStream(1, listOf(Poll.Pending), trace))
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertEquals(listOf(0, 1), trace)
        assertFalse(stream.isTerminated())
    }
}
