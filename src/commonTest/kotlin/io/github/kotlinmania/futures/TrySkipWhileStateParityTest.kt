package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TrySkipWhileStateParityTest {
    private class Input(
        events: List<Poll<Yield<Try<Int?, String>>>>,
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
            return SizeHint(count, count)
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
    fun pendingNullHasAnUpperHintAndFalseYieldsItBeforeDirectDelegation() {
        val input = Input(listOf(value(Try.Ok(null)), value(Try.Ok(7))))
        val gate = Gate(Try.Ok(false))
        var calls = 0
        val stream =
            input.trySkipWhile {
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
        assertEquals(1, calls)
        assertTrue(stream.isTerminated())
    }

    @Test
    fun predicateErrorRetainsPendingNullThroughInputEnd() {
        val input = Input(listOf(value(Try.Ok(null))))
        val stream = input.trySkipWhile { ready<Try<Boolean, String>>(Try.Err("predicate")) }
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
        val stream = input.trySkipWhile { throw failure }
        assertSame(failure, assertFailsWith<IllegalStateException> { stream.pollNext(TaskContext()) })
        assertEquals(1, input.polls)
        assertEquals(SizeHint(0, 1), stream.sizeHint())
    }

    @Test
    fun pollingFailureRetainsPendingNull() {
        val failure = IllegalStateException("future")
        val action =
            object : Future<Try<Boolean, String>> {
                override fun poll(context: TaskContext): Poll<Try<Boolean, String>> = throw failure
            }
        val input = Input(listOf(value(Try.Ok(null))))
        val stream = input.trySkipWhile { action }
        assertSame(failure, assertFailsWith<IllegalStateException> { stream.pollNext(TaskContext()) })
        assertFalse(stream.isTerminated())
        assertEquals(SizeHint(0, 1), stream.sizeHint())
    }

    @Test
    fun inputErrorDoesNotDiscardItemRetainedAfterPredicateError() {
        val input = Input(listOf(value(Try.Ok(null)), value(Try.Err("input")), Poll.Ready(Yield.End)))
        var calls = 0
        val stream =
            input.trySkipWhile {
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
    fun trueSkipsNullThenFalseYieldsNextItemWithoutReadingAhead() {
        val input = Input(listOf(value(Try.Ok(null)), value(Try.Ok(7)), value(Try.Ok(8))))
        val calls = mutableListOf<Int?>()
        val stream =
            input.trySkipWhile {
                calls.add(it)
                ready<Try<Boolean, String>>(Try.Ok(it == null))
            }
        assertEquals(value(Try.Ok(7)), stream.pollNext(TaskContext()))
        assertEquals(2, input.polls)
        assertEquals(listOf(null, 7), calls)
        assertEquals(SizeHint(0, 1), stream.sizeHint())
        assertEquals(value(Try.Ok(8)), stream.pollNext(TaskContext()))
        assertEquals(listOf(null, 7), calls)
    }
}
