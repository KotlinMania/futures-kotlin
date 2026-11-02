package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TryUnfoldStateParityTest {
    private class Script(
        private val trace: MutableList<String>,
        events: List<Poll<Try<Pair<Int?, Int?>?, String?>>>,
        private var panicFirst: Boolean = false,
    ) : Future<Try<Pair<Int?, Int?>?, String?>> {
        private val events = events.toMutableList()

        override fun poll(context: TaskContext): Poll<Try<Pair<Int?, Int?>?, String?>> {
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
        events: List<Poll<Try<Pair<Int?, Int?>?, String?>>>,
        panicFirst: Boolean = false,
    ): Script {
        trace.add("call:${state ?: "null"}")
        return Script(trace, events, panicFirst)
    }

    private fun callbackPanic(init: Int?, expected: String) {
        val trace = mutableListOf<String>()
        val stream =
            tryStreamUnfold<Int?, Int?, String?>(init) { state ->
                trace.add("call:${state ?: "null"}")
                error("callback panic")
            }
        val context = TaskContext()
        val error = assertFailsWith<IllegalStateException> { stream.pollNext(context) }
        assertEquals("callback panic", error.message)
        repeat(2) { assertEquals(Poll.Ready(Yield.End), stream.pollNext(context)) }
        assertEquals(listOf(expected), trace)
    }

    @Test
    fun callbackPanicConsumesSeed() = callbackPanic(0, "call:0")

    @Test
    fun nullableCallbackPanicConsumesSeed() = callbackPanic(null, "call:null")

    @Test
    fun callbackPanicAfterItemConsumesNextSeed() {
        val trace = mutableListOf<String>()
        val stream =
            tryStreamUnfold<Int?, Int?, String?>(0) { state ->
                if (state == 0) {
                    scripted(trace, state, listOf(Poll.Ready(Try.Ok(Pair(7, 1)))))
                } else {
                    trace.add("call:1")
                    error("callback panic")
                }
            }
        val context = TaskContext()
        assertEquals(Poll.Ready(Yield.Value(Try.Ok(7))), stream.pollNext(context))
        val error = assertFailsWith<IllegalStateException> { stream.pollNext(context) }
        assertEquals("callback panic", error.message)
        repeat(2) { assertEquals(Poll.Ready(Yield.End), stream.pollNext(context)) }
        assertEquals(listOf("call:0", "future", "call:1"), trace)
    }

    @Test
    fun pendingAndEofAreRetained() {
        val trace = mutableListOf<String>()
        val stream =
            tryStreamUnfold<Int?, Int?, String?>(0) { state ->
                val events =
                    if (state == 0) {
                        listOf(Poll.Pending, Poll.Pending, Poll.Ready(Try.Ok(Pair(7, 1))))
                    } else {
                        listOf(Poll.Ready(Try.Ok(null)))
                    }
                scripted(trace, state, events)
            }
        var wakes = 0
        val context = TaskContext(Waker { wakes++ })
        assertSame(Poll.Pending, stream.pollNext(context))
        assertSame(Poll.Pending, stream.pollNext(context))
        assertEquals(Poll.Ready(Yield.Value(Try.Ok(7))), stream.pollNext(context))
        repeat(3) { assertEquals(Poll.Ready(Yield.End), stream.pollNext(context)) }
        assertEquals(listOf("call:0", "future", "future", "future", "call:1", "future"), trace)
        assertEquals(2, wakes)
    }

    @Test
    fun nullErrorIsYieldedOnce() {
        val trace = mutableListOf<String>()
        val stream =
            tryStreamUnfold<Int?, Int?, String?>(0) { state ->
                scripted(trace, state, listOf(Poll.Ready(Try.Err(null))))
            }
        val context = TaskContext()
        assertEquals(Poll.Ready(Yield.Value(Try.Err(null))), stream.pollNext(context))
        repeat(2) { assertEquals(Poll.Ready(Yield.End), stream.pollNext(context)) }
        assertEquals(listOf("call:0", "future"), trace)
    }

    @Test
    fun nullableItemAndStateAreNotEof() {
        val trace = mutableListOf<String>()
        var calls = 0
        val stream =
            tryStreamUnfold<Int?, Int?, String?>(null) { state ->
                calls++
                val step = if (calls == 1) Pair(null, null) else null
                scripted(trace, state, listOf(Poll.Ready(Try.Ok(step))))
            }
        val context = TaskContext()
        assertEquals(Poll.Ready(Yield.Value(Try.Ok(null))), stream.pollNext(context))
        repeat(2) { assertEquals(Poll.Ready(Yield.End), stream.pollNext(context)) }
        assertEquals(listOf("call:null", "future", "call:null", "future"), trace)
    }

    @Test
    fun futurePanicRetainsActiveFuture() {
        val trace = mutableListOf<String>()
        val stream =
            tryStreamUnfold<Int?, Int?, String?>(0) { state ->
                scripted(trace, state, listOf(Poll.Ready(Try.Ok(Pair(7, 1)))), panicFirst = true)
            }
        val context = TaskContext()
        val error = assertFailsWith<IllegalStateException> { stream.pollNext(context) }
        assertEquals("future panic", error.message)
        assertEquals(Poll.Ready(Yield.Value(Try.Ok(7))), stream.pollNext(context))
        assertEquals(listOf("call:0", "future", "future"), trace)
    }

    @Test
    fun ordinaryAndTryTerminalBehaviorDiffer() {
        val ordinary = streamUnfold<Int?, Int?>(0) { ready(null) }
        val fallible = tryStreamUnfold<Int?, Int?, String?>(0) { ready(Try.Ok(null)) }
        val context = TaskContext()
        assertEquals(Poll.Ready(Yield.End), ordinary.pollNext(context))
        assertEquals(Poll.Ready(Yield.End), fallible.pollNext(context))
        val error = assertFailsWith<IllegalStateException> { ordinary.pollNext(context) }
        assertTrue(requireNotNull(error.message).startsWith("Unfold must not be polled after it returned"))
        assertEquals(Poll.Ready(Yield.End), fallible.pollNext(context))
    }
}
