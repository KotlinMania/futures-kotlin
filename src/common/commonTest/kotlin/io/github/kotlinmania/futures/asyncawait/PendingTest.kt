// port-lint: tests async_await/pending.rs
package io.github.kotlinmania.futures.asyncawait

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingTest {
    @Test
    fun pendingOnceReturnsPendingThenReady() {
        val ctx = TaskContext()
        val future = pendingOnce()
        assertFalse(future.isReady)
        assertEquals(Poll.Pending, future.poll(ctx))
        assertTrue(future.isReady)
        assertEquals(Poll.Ready(Unit), future.poll(ctx))
        assertEquals(Poll.Ready(Unit), future.poll(ctx))
    }
}
