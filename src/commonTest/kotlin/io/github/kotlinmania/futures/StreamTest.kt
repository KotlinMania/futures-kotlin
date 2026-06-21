package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamTest {
    private class ListStream(
        private val items: List<Int>,
    ) : Stream<Int> {
        private var index = 0

        override fun pollNext(context: TaskContext): Poll<Yield<Int>> =
            if (index < items.size) {
                Poll.ready(Yield.value(items[index++]))
            } else {
                Poll.ready(Yield.end())
            }

        override fun sizeHint(): SizeHint {
            val remaining = items.size - index
            return SizeHint(lower = remaining, upper = remaining)
        }
    }

    @Test
    fun yieldsValuesUntilEnd() {
        val stream = ListStream(listOf(1, 2, 3))
        val context = TaskContext()

        assertEquals(Poll.Ready(Yield.Value(1)), stream.pollNext(context))
        assertEquals(Poll.Ready(Yield.Value(2)), stream.pollNext(context))
        assertEquals(Poll.Ready(Yield.Value(3)), stream.pollNext(context))
        assertEquals(Poll.Ready(Yield.End), stream.pollNext(context))
    }

    @Test
    fun pendingStreamWakesContext() {
        var woke = false
        val context = TaskContext(Waker { woke = true })
        val stream =
            object : Stream<Int> {
                override fun pollNext(context: TaskContext): Poll<Yield<Int>> {
                    context.wakeByRef()
                    return Poll.pending()
                }
            }

        assertEquals(Poll.Pending, stream.pollNext(context))
        assertTrue(woke)
    }

    @Test
    fun defaultSizeHintIsZeroAndUnbounded() {
        val stream =
            object : Stream<Int> {
                override fun pollNext(context: TaskContext): Poll<Yield<Int>> =
                    Poll.ready(Yield.end())
            }

        val hint = stream.sizeHint()
        assertEquals(0, hint.lower)
        assertNull(hint.upper)
    }

    @Test
    fun overriddenSizeHintShrinksAsItemsConsumed() {
        val stream = ListStream(listOf(10, 20))
        assertEquals(SizeHint(2, 2), stream.sizeHint())

        stream.pollNext(TaskContext())
        assertEquals(SizeHint(1, 1), stream.sizeHint())

        stream.pollNext(TaskContext())
        assertEquals(SizeHint(0, 0), stream.sizeHint())
    }

    @Test
    fun fusedStreamReportsTermination() {
        class OnceStream : FusedStream<Int> {
            private var produced = false
            private var ended = false

            override fun pollNext(context: TaskContext): Poll<Yield<Int>> =
                when {
                    !produced -> {
                        produced = true
                        Poll.ready(Yield.value(99))
                    }
                    else -> {
                        ended = true
                        Poll.ready(Yield.end())
                    }
                }

            override fun isTerminated(): Boolean = ended
        }

        val stream = OnceStream()
        assertFalse(stream.isTerminated())
        assertEquals(Poll.Ready(Yield.Value(99)), stream.pollNext(TaskContext()))
        assertFalse(stream.isTerminated())
        assertEquals(Poll.Ready(Yield.End), stream.pollNext(TaskContext()))
        assertTrue(stream.isTerminated())
    }

    @Test
    fun tryStreamViewDelegatesToUnderlyingStream() {
        val items = listOf<Try<Int, String>>(Try.Ok(1), Try.Err("boom"))
        var idx = 0
        val source: Stream<Try<Int, String>> =
            object : Stream<Try<Int, String>> {
                override fun pollNext(context: TaskContext): Poll<Yield<Try<Int, String>>> =
                    if (idx < items.size) {
                        Poll.ready(Yield.value(items[idx++]))
                    } else {
                        Poll.ready(Yield.end())
                    }
            }

        val tryStream = source.asTryStream()
        assertEquals(
            Poll.Ready(Yield.Value(Try.Ok(1))),
            tryStream.tryPollNext(TaskContext()),
        )
        assertEquals(
            Poll.Ready(Yield.Value(Try.Err("boom"))),
            tryStream.tryPollNext(TaskContext()),
        )
        assertEquals(Poll.Ready(Yield.End), tryStream.tryPollNext(TaskContext()))
    }

    @Test
    fun yieldFoldHandlesBothBranches() {
        val v: Yield<Int> = Yield.Value(7)
        val e: Yield<Int> = Yield.End

        assertEquals("got 7", v.fold(onValue = { "got $it" }, onEnd = { "done" }))
        assertEquals("done", e.fold(onValue = { "got $it" }, onEnd = { "done" }))
    }

    @Test
    fun valueOrNullReturnsValueOrNull() {
        val v: Yield<Int> = Yield.Value(42)
        val e: Yield<Int> = Yield.End

        assertEquals(42, v.valueOrNull())
        assertNull(e.valueOrNull())
        assertNotNull(v.valueOrNull())
    }
}
