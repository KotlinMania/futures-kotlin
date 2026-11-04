package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TryTakeWhileStateParityTest {
    private class Input(
        events: List<Poll<Yield<Try<Int?, String>>>>,
        private val upperHint: Int? = null,
    ) : TryStream<Int?, String>,
        FusedStream<Try<Int?, String>> {
        private val events = events.toMutableList()
        var polls = 0
            private set

        override fun pollNext(context: TaskContext): Poll<Yield<Try<Int?, String>>> {
            polls += 1
            return if (events.isEmpty()) Poll.Ready(Yield.End) else events.removeAt(0)
        }

        override fun isTerminated(): Boolean = events.isEmpty()

        override fun sizeHint(): SizeHint {
            val count = events.count { it is Poll.Ready && it.value is Yield.Value }
            return SizeHint(count, upperHint ?: count)
        }
    }

    private class Gate(
        private val result: Try<Boolean, String>,
    ) : Future<Try<Boolean, String>> {
        var released = false
        private var waker: Waker? = null

        fun release() {
            released = true
            waker?.wakeByRef()
        }

        override fun poll(context: TaskContext): Poll<Try<Boolean, String>> {
            if (!released) {
                waker = context.waker
                return Poll.Pending
            }
            return Poll.Ready(result)
        }
    }

    private fun value(value: Try<Int?, String>): Poll<Yield<Try<Int?, String>>> = Poll.Ready(Yield.Value(value))

    @Test
    fun pendingNullHasAnUpperHintAndTrueYieldsItBeforeTheNextItem() {
        val input = Input(listOf(value(Try.Ok(null)), value(Try.Ok(7))))
        val gate = Gate(Try.Ok(true))
        var calls = 0
        val stream =
            input.tryTakeWhile {
                calls += 1
                gate
            }
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertEquals(SizeHint(0, 2), stream.sizeHint())
        assertEquals(1, input.polls)
        gate.release()
        assertEquals(value(Try.Ok(null)), stream.pollNext(TaskContext()))
        assertEquals(SizeHint(0, 1), stream.sizeHint())
        assertEquals(value(Try.Ok(7)), stream.pollNext(TaskContext()))
        assertEquals(2, calls)
        assertTrue(stream.isTerminated())
    }

    @Test
    fun predicateErrorRetainsPendingNullThroughInputEnd() {
        val input = Input(listOf(value(Try.Ok(null))))
        val stream = input.tryTakeWhile { ready<Try<Boolean, String>>(Try.Err("predicate")) }
        assertEquals(value(Try.Err("predicate")), stream.pollNext(TaskContext()))
        assertTrue(input.isTerminated())
        assertFalse(stream.isTerminated())
        assertEquals(SizeHint(0, 1), stream.sizeHint())
        assertEquals(Poll.Ready(Yield.End), stream.pollNext(TaskContext()))
        assertFalse(stream.isTerminated())
        assertEquals(SizeHint(0, 1), stream.sizeHint())
    }

    @Test
    fun creationFailureDoesNotCacheConsumedItem() {
        val failure = IllegalStateException("predicate")
        val input = Input(listOf(value(Try.Ok(1)), value(Try.Ok(2))))
        val stream = input.tryTakeWhile { throw failure }
        assertSame(failure, assertFailsWith<IllegalStateException> { stream.pollNext(TaskContext()) })
        assertEquals(1, input.polls)
        assertEquals(SizeHint(0, 1), stream.sizeHint())
    }

    @Test
    fun pollingFailureRetainsPendingNull() {
        val failure = IllegalStateException("future")
        val action = Future<Try<Boolean, String>> { throw failure }
        val input = Input(listOf(value(Try.Ok(null))))
        val stream = input.tryTakeWhile { action }
        assertSame(failure, assertFailsWith<IllegalStateException> { stream.pollNext(TaskContext()) })
        assertFalse(stream.isTerminated())
        assertEquals(SizeHint(0, 1), stream.sizeHint())
    }

    @Test
    fun inputErrorDoesNotDiscardItemRetainedAfterPredicateError() {
        val input = Input(listOf(value(Try.Ok(null)), value(Try.Err("input")), Poll.Ready(Yield.End)))
        var calls = 0
        val stream =
            input.tryTakeWhile {
                calls += 1
                ready<Try<Boolean, String>>(Try.Err("predicate"))
            }
        assertEquals(value(Try.Err("predicate")), stream.pollNext(TaskContext()))
        assertEquals(SizeHint(0, 2), stream.sizeHint())
        assertEquals(value(Try.Err("input")), stream.pollNext(TaskContext()))
        assertEquals(SizeHint(0, 1), stream.sizeHint())
        assertEquals(Poll.Ready(Yield.End), stream.pollNext(TaskContext()))
        assertEquals(SizeHint(0, 1), stream.sizeHint())
        assertFalse(stream.isTerminated())
        assertEquals(1, calls)
    }

    @Test
    fun falseStopsWithoutReadingAheadOrCallingPredicateAgain() {
        val input = Input(listOf(value(Try.Ok(null)), value(Try.Ok(7))))
        val gate = Gate(Try.Ok(false))
        var calls = 0
        val stream =
            input.tryTakeWhile {
                calls += 1
                gate
            }
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        gate.release()
        assertEquals(Poll.Ready(Yield.End), stream.pollNext(TaskContext()))
        assertEquals(SizeHint(0, 0), stream.sizeHint())
        assertTrue(stream.isTerminated())
        assertEquals(Poll.Ready(Yield.End), stream.pollNext(TaskContext()))
        assertEquals(1, input.polls)
        assertEquals(1, calls)
    }

    @Test
    fun pendingItemMakesAnOverflowingUpperHintUnknown() {
        val input = Input(listOf(value(Try.Ok(null))), upperHint = Int.MAX_VALUE)
        val gate = Gate(Try.Ok(true))
        val stream = input.tryTakeWhile { gate }
        assertEquals(SizeHint(0, Int.MAX_VALUE), stream.sizeHint())
        assertSame(Poll.Pending, stream.pollNext(TaskContext()))
        assertEquals(SizeHint(0, null), stream.sizeHint())
        assertFalse(stream.isTerminated())
    }
}
