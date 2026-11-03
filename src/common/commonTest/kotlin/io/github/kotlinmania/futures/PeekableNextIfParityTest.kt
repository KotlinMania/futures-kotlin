package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PeekableNextIfParityTest {
    private fun item(poll: Poll<Yield<Int?>>): Int? = assertIs<Yield.Value<Int?>>(assertIs<Poll.Ready<Yield<Int?>>>(poll).value).value

    @Test
    fun acceptedNullableItemCallsPredicateAndConsumesItsPresence() {
        val stream = streamIter(listOf<Int?>(null, 7)).peekable()
        var calls = 0
        val future =
            stream.nextIf {
                calls += 1
                assertNull(it)
                true
            }
        assertNull(assertIs<Poll.Ready<Int?>>(future.poll(TaskContext())).value)
        assertEquals(1, calls)
        assertTrue(assertIs<FusedFuture<Int?>>(future).isTerminated())
        assertFailsWith<IllegalStateException> { future.poll(TaskContext()) }
        assertEquals(7, item(stream.pollNext(TaskContext())))
    }

    @Test
    fun rejectedNullableItemCallsPredicateAndIsRestored() {
        val stream = streamIter(listOf<Int?>(null, 7)).peekable()
        var calls = 0
        val future =
            stream.nextIf {
                calls += 1
                assertNull(it)
                false
            }
        assertNull(assertIs<Poll.Ready<Int?>>(future.poll(TaskContext())).value)
        assertEquals(1, calls)
        assertNull(item(stream.pollNext(TaskContext())))
        assertEquals(7, item(stream.pollNext(TaskContext())))
    }

    @Test
    fun nextIfEqConsumesAMatchingNullItem() {
        val stream = streamIter(listOf<Int?>(null, 7)).peekable()
        assertNull(assertIs<Poll.Ready<Int?>>(stream.nextIfEq(null).poll(TaskContext())).value)
        assertEquals(7, item(stream.pollNext(TaskContext())))
    }

    @Test
    fun predicateFailureConsumesTheItemAndTerminatesTheConditionalFuture() {
        val stream = streamIter(listOf<Int?>(1, 2)).peekable()
        val failure = IllegalStateException("predicate")
        var calls = 0
        val future =
            stream.nextIf {
                calls += 1
                throw failure
            }
        assertSame(failure, assertFailsWith<IllegalStateException> { future.poll(TaskContext()) })
        assertTrue(assertIs<FusedFuture<Int?>>(future).isTerminated())
        assertEquals("NextIf polled after completion", assertFailsWith<IllegalStateException> { future.poll(TaskContext()) }.message)
        assertEquals(1, calls)
        assertEquals(2, item(stream.pollNext(TaskContext())))
    }

    @Test
    fun pendingDoesNotCallPredicateAndRejectionRestoresTheEventualItem() {
        var polls = 0
        val input =
            object : Stream<Int?> {
                override fun pollNext(context: TaskContext): Poll<Yield<Int?>> {
                    polls += 1
                    if (polls == 1) {
                        context.wakeByRef()
                        return Poll.Pending
                    }
                    return Poll.Ready(Yield.Value(3))
                }
            }
        val stream = input.peekable()
        var calls = 0
        val future =
            stream.nextIf {
                calls += 1
                false
            }
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(0, calls)
        assertNull(assertIs<Poll.Ready<Int?>>(future.poll(TaskContext())).value)
        assertEquals(1, calls)
        assertEquals(3, item(stream.pollNext(TaskContext())))
        assertEquals(2, polls)
    }

    @Test
    fun endedInputIsFusedAcrossRepeatedPeeksAndPolls() {
        var polls = 0
        val input =
            object : Stream<Int?> {
                override fun pollNext(context: TaskContext): Poll<Yield<Int?>> {
                    polls += 1
                    check(polls == 1) { "input polled after end" }
                    return Poll.Ready(Yield.End)
                }
            }
        val stream = input.peekable()
        repeat(2) { assertNull(assertIs<Poll.Ready<Int?>>(stream.pollPeek(TaskContext())).value) }
        assertSame(Yield.End, assertIs<Poll.Ready<Yield<Int?>>>(stream.pollNext(TaskContext())).value)
        assertTrue(stream.isTerminated())
        assertEquals(SizeHint(0, 0), stream.sizeHint())
        assertEquals(1, polls)
    }
}
