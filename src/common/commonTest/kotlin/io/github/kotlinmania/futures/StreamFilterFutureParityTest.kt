package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StreamFilterFutureParityTest {
    private class Input(
        events: List<Poll<Yield<Int?>>>,
    ) : FusedStream<Int?> {
        private val events = events.toMutableList()
        var polls = 0
            private set

        override fun isTerminated(): Boolean = events.isEmpty()

        override fun pollNext(context: TaskContext): Poll<Yield<Int?>> {
            polls += 1
            val event = if (events.isEmpty()) Poll.Ready(Yield.End) else events.removeAt(0)
            if (event is Poll.Pending) context.wakeByRef()
            return event
        }

        override fun sizeHint(): SizeHint {
            val count = events.count { it is Poll.Ready && it.value is Yield.Value }
            return SizeHint(count, count)
        }
    }

    private class Gate(
        private val result: Boolean,
    ) : Future<Boolean> {
        private var released = false
        private var waker: Waker? = null
        var polls = 0
            private set

        fun release() {
            released = true
            waker?.wakeByRef()
        }

        override fun poll(context: TaskContext): Poll<Boolean> {
            polls += 1
            if (!released) {
                waker = context.waker
                return Poll.Pending
            }
            return Poll.Ready(result)
        }
    }

    private fun item(poll: Poll<Yield<Int?>>): Int? = assertIs<Yield.Value<Int?>>(assertIs<Poll.Ready<Yield<Int?>>>(poll).value).value

    @Test
    fun pendingPredicateRetainsNullItemAndItsUpperHintWithoutReadingAhead() {
        val input = Input(listOf(Poll.Ready(Yield.Value(null)), Poll.Ready(Yield.Value(7)), Poll.Ready(Yield.End)))
        val gate = Gate(true)
        val calls = mutableListOf<Int?>()
        val stream =
            input.filter {
                calls.add(it)
                if (it == null) gate else ready(true)
            }
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertEquals(listOf<Int?>(null), calls)
        assertEquals(1, input.polls)
        assertEquals(SizeHint(0, 2), stream.sizeHint())
        gate.release()
        assertNull(item(stream.pollNext(TaskContext())))
        assertEquals(1, input.polls)
        assertEquals(SizeHint(0, 1), stream.sizeHint())
        assertEquals(7, item(stream.pollNext(TaskContext())))
        assertEquals(listOf(null, 7), calls)
        assertEquals(2, gate.polls)
    }

    @Test
    fun rejectedPendingNullIsDiscardedBeforeTheNextItemIsTested() {
        val input = Input(listOf(Poll.Ready(Yield.Value(null)), Poll.Ready(Yield.Value(7)), Poll.Ready(Yield.End)))
        val gate = Gate(false)
        val calls = mutableListOf<Int?>()
        val stream =
            input.filter {
                calls.add(it)
                if (it == null) gate else ready(true)
            }
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        gate.release()
        assertEquals(7, item(stream.pollNext(TaskContext())))
        assertEquals(listOf(null, 7), calls)
        assertEquals(2, input.polls)
    }

    @Test
    fun pendingPredicatePreventsTerminationEvenWhenInputIsExhausted() {
        val input = Input(listOf(Poll.Ready(Yield.Value(1))))
        val gate = Gate(true)
        val stream = input.filter { gate }
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertTrue(input.isTerminated())
        assertFalse(stream.isTerminated())
        assertEquals(SizeHint(0, 1), stream.sizeHint())
        gate.release()
        assertEquals(1, item(stream.pollNext(TaskContext())))
        assertTrue(stream.isTerminated())
        assertEquals(SizeHint(0, 0), stream.sizeHint())
    }

    @Test
    fun predicateCreationFailureDoesNotCacheTheConsumedItem() {
        val failure = IllegalStateException("predicate")
        val input = Input(listOf(Poll.Ready(Yield.Value(1)), Poll.Ready(Yield.Value(2))))
        val stream = input.filter { throw failure }
        assertSame(failure, assertFailsWith<IllegalStateException> { stream.pollNext(TaskContext()) })
        assertEquals(1, input.polls)
        assertEquals(SizeHint(0, 1), stream.sizeHint())
        assertFalse(stream.isTerminated())
    }

    @Test
    fun predicateFutureFailureRetainsItsPendingItemAndFuture() {
        val failure = IllegalStateException("predicate future")
        val action =
            object : Future<Boolean> {
                override fun poll(context: TaskContext): Poll<Boolean> = throw failure
            }
        val input = Input(listOf(Poll.Ready(Yield.Value(null))))
        val stream = input.filter { action }
        assertSame(failure, assertFailsWith<IllegalStateException> { stream.pollNext(TaskContext()) })
        assertEquals(SizeHint(0, 1), stream.sizeHint())
        assertFalse(stream.isTerminated())
    }

    @Test
    fun inputPendingThenEndDoesNotCreatePredicateFuture() {
        val input = Input(listOf(Poll.Pending, Poll.Ready(Yield.End)))
        val stream = input.filter { error("not called") }
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertSame(Yield.End, assertIs<Poll.Ready<Yield<Int?>>>(stream.pollNext(TaskContext())).value)
        assertTrue(stream.isTerminated())
    }
}
