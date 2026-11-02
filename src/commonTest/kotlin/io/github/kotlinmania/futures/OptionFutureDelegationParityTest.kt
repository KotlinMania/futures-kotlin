package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OptionFutureDelegationParityTest {
    @Test
    fun repeatedlyPollsAnAlwaysReadyProducer() {
        var calls = 0
        val future = alwaysReady { ++calls }.intoOptionFuture()
        val context = TaskContext()
        assertEquals(1, assertIs<Poll.Ready<Int?>>(future.poll(context)).value)
        assertEquals(2, assertIs<Poll.Ready<Int?>>(future.poll(context)).value)
        assertEquals(3, assertIs<Poll.Ready<Int?>>(future.poll(context)).value)
        assertEquals(3, calls)
    }

    @Test
    fun alwaysReadyInnerDoesNotBecomeTerminated() {
        val future = alwaysReady { 7 }.intoOptionFuture()
        assertFalse(future.isTerminated())
        assertEquals(7, assertIs<Poll.Ready<Int?>>(future.poll(TaskContext())).value)
        assertFalse(future.isTerminated())
    }

    @Test
    fun oneShotInnerTerminationIsForwarded() {
        val inner = ready(9)
        val future = inner.intoOptionFuture()
        assertFalse(future.isTerminated())
        assertEquals(9, assertIs<Poll.Ready<Int?>>(future.poll(TaskContext())).value)
        assertTrue(inner.isTerminated())
        assertTrue(future.isTerminated())
    }

    @Test
    fun pendingPollUsesTheProvidedContext() {
        val expectedContext = TaskContext()
        var calls = 0
        val inner =
            object : Future<Int> {
                override fun poll(context: TaskContext): Poll<Int> {
                    assertSame(expectedContext, context)
                    calls += 1
                    if (calls == 1) {
                        context.wakeByRef()
                        return Poll.Pending
                    }
                    return Poll.Ready(12)
                }
            }
        val future = inner.intoOptionFuture()
        assertSame(Poll.Pending, future.poll(expectedContext))
        assertEquals(12, assertIs<Poll.Ready<Int?>>(future.poll(expectedContext)).value)
        assertEquals(2, calls)
    }

    @Test
    fun absentInnerRemainsReadyAndTerminated() {
        val future = OptionFuture<Int>(null)
        repeat(3) {
            assertTrue(future.isTerminated())
            assertNull(assertIs<Poll.Ready<Int?>>(future.poll(TaskContext())).value)
        }
    }
}
