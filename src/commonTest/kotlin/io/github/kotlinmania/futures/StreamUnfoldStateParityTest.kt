package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StreamUnfoldStateParityTest {
    private class Script(
        private val trace: MutableList<String>,
        events: List<Poll<Pair<Int?, Int?>?>>,
        private var panicFirst: Boolean = false,
    ) : Future<Pair<Int?, Int?>?> {
        private val events = events.toMutableList()

        override fun poll(context: TaskContext): Poll<Pair<Int?, Int?>?> {
            trace.add("future")
            if (panicFirst) {
                panicFirst = false
                error("future panic")
            }
            check(events.isNotEmpty()) { "unexpected future repoll" }
            val result = events.removeAt(0)
            if (result === Poll.Pending) context.wakeByRef()
            return result
        }
    }

    private fun scripted(
        trace: MutableList<String>,
        state: Int?,
        events: List<Poll<Pair<Int?, Int?>?>>,
        panicFirst: Boolean = false,
    ): Script {
        trace.add("call:${state ?: "null"}")
        return Script(trace, events, panicFirst)
    }

    private fun terminalPanic(stream: Stream<Int?>, context: TaskContext) {
        val error = assertFailsWith<IllegalStateException> { stream.pollNext(context) }
        assertTrue(requireNotNull(error.message).startsWith("Unfold must not be polled after it returned"))
    }

    @Test
    fun immediateEofRepollPanics() {
        val trace = mutableListOf<String>()
        val stream = streamUnfold<Int?, Int?>(0) { scripted(trace, it, listOf(Poll.Ready(null))) }
        val context = TaskContext()
        assertFalse(stream.isTerminated())
        assertEquals(Poll.Ready(Yield.End), stream.pollNext(context))
        assertTrue(stream.isTerminated())
        terminalPanic(stream, context)
        assertEquals(listOf("call:0", "future"), trace)
    }

    @Test
    fun valueThenEofRepollPanics() {
        val trace = mutableListOf<String>()
        val stream =
            streamUnfold<Int?, Int?>(0) { state ->
                val step = if (state == 0) Pair(7, 1) else null
                scripted(trace, state, listOf(Poll.Ready(step)))
            }
        val context = TaskContext()
        assertEquals(Poll.Ready(Yield.Value(7)), stream.pollNext(context))
        assertFalse(stream.isTerminated())
        assertEquals(Poll.Ready(Yield.End), stream.pollNext(context))
        assertTrue(stream.isTerminated())
        terminalPanic(stream, context)
        assertEquals(listOf("call:0", "future", "call:1", "future"), trace)
    }

    @Test
    fun callbackPanicConsumesSeed() {
        val trace = mutableListOf<String>()
        val stream =
            streamUnfold<Int?, Int?>(0) { state ->
                trace.add("call:$state")
                error("callback panic")
            }
        val context = TaskContext()
        val error = assertFailsWith<IllegalStateException> { stream.pollNext(context) }
        assertEquals("callback panic", error.message)
        assertTrue(stream.isTerminated())
        terminalPanic(stream, context)
        assertEquals(listOf("call:0"), trace)
    }

    @Test
    fun callbackPanicAfterValueConsumesNextSeed() {
        val trace = mutableListOf<String>()
        val stream =
            streamUnfold<Int?, Int?>(0) { state ->
                if (state == 0) {
                    scripted(trace, state, listOf(Poll.Ready(Pair(7, 1))))
                } else {
                    trace.add("call:1")
                    error("callback panic")
                }
            }
        val context = TaskContext()
        assertEquals(Poll.Ready(Yield.Value(7)), stream.pollNext(context))
        assertFalse(stream.isTerminated())
        val error = assertFailsWith<IllegalStateException> { stream.pollNext(context) }
        assertEquals("callback panic", error.message)
        assertTrue(stream.isTerminated())
        terminalPanic(stream, context)
        assertEquals(listOf("call:0", "future", "call:1"), trace)
    }

    @Test
    fun pendingNullableItemAndSeed() {
        val trace = mutableListOf<String>()
        val stream =
            streamUnfold<Int?, Int?>(0) { state ->
                val events =
                    if (state == 0) {
                        listOf(Poll.Pending, Poll.Pending, Poll.Ready(Pair(null, null)))
                    } else {
                        listOf(Poll.Ready(null))
                    }
                scripted(trace, state, events)
            }
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertSame(Poll.Pending, stream.pollNext(context))
        assertFalse(stream.isTerminated())
        assertSame(Poll.Pending, stream.pollNext(context))
        assertEquals(Poll.Ready(Yield.Value(null)), stream.pollNext(context))
        assertFalse(stream.isTerminated())
        assertEquals(Poll.Ready(Yield.End), stream.pollNext(context))
        terminalPanic(stream, context)
        assertEquals(listOf("call:0", "future", "future", "future", "call:null", "future"), trace)
        assertEquals(2, wakes)
    }

    @Test
    fun futurePanicKeepsActiveFuture() {
        val trace = mutableListOf<String>()
        val stream =
            streamUnfold<Int?, Int?>(0) { state ->
                scripted(trace, state, listOf(Poll.Ready(Pair(7, 1))), panicFirst = true)
            }
        val context = TaskContext()
        val error = assertFailsWith<IllegalStateException> { stream.pollNext(context) }
        assertEquals("future panic", error.message)
        assertFalse(stream.isTerminated())
        assertEquals(Poll.Ready(Yield.Value(7)), stream.pollNext(context))
        assertEquals(listOf("call:0", "future", "future"), trace)
    }

    @Test
    fun canonicalFuseStabilizesEof() {
        val trace = mutableListOf<String>()
        val stream =
            streamUnfold<Int?, Int?>(null) { scripted(trace, it, listOf(Poll.Ready(null))) }
                .fuse()
        val context = TaskContext()
        assertFalse(stream.isTerminated())
        repeat(3) {
            assertEquals(Poll.Ready(Yield.End), stream.pollNext(context))
            assertTrue(stream.isTerminated())
        }
        assertEquals(listOf("call:null", "future"), trace)
    }

    @Test
    fun nonnullSequenceControl() {
        val trace = mutableListOf<String>()
        val stream =
            streamUnfold<Int?, Int?>(0) { state ->
                val step = if (state != null && state < 2) Pair(state * 2, state + 1) else null
                scripted(trace, state, listOf(Poll.Ready(step)))
            }
        val context = TaskContext()
        assertEquals(Poll.Ready(Yield.Value(0)), stream.pollNext(context))
        assertEquals(Poll.Ready(Yield.Value(2)), stream.pollNext(context))
        assertEquals(Poll.Ready(Yield.End), stream.pollNext(context))
        assertTrue(stream.isTerminated())
        assertEquals(listOf("call:0", "future", "call:1", "future", "call:2", "future"), trace)
    }
}
