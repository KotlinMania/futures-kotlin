package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutureTest {
    @Test
    fun readyFutureProducesValue() {
        var polls = 0
        val future =
            Future<Int> {
                polls += 1
                Poll.ready(42)
            }

        assertEquals(Poll.Ready(42), future.poll(TaskContext()))
        assertEquals(1, polls)
    }

    @Test
    fun pendingFutureCanWakeContext() {
        var woke = false
        val context = TaskContext(Waker { woke = true })
        val future =
            Future<Int> {
                it.wakeByRef()
                Poll.pending()
            }

        assertEquals(Poll.Pending, future.poll(context))
        assertTrue(woke)
        assertNull(future.poll(TaskContext()).readyOrNull())
    }

    @Test
    fun fusedFutureReportsTermination() {
        class OnceFuture : FusedFuture<Int> {
            private var done = false

            override fun poll(context: TaskContext): Poll<Int> =
                if (done) {
                    Poll.pending()
                } else {
                    done = true
                    Poll.ready(7)
                }

            override fun isTerminated(): Boolean = done
        }

        val future = OnceFuture()
        assertFalse(future.isTerminated())
        assertEquals(Poll.Ready(7), future.poll(TaskContext()))
        assertTrue(future.isTerminated())
        assertEquals(Poll.Pending, future.poll(TaskContext()))
    }

    @Test
    fun tryFutureDelegatesToUnderlyingFuture() {
        val value: Try<Int, String> = Try.Ok(5)
        val future = Future<Try<Int, String>> { Poll.ready(value) }

        assertEquals(Poll.Ready(value), future.asTryFuture().tryPoll(TaskContext()))
    }
}
