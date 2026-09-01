// port-lint: source future/poll_fn.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PollFnTest {
    @Test
    fun testPollFn() {
        var count = 0
        val fut =
            pollFn {
                count++
                if (count >= 2) Poll.Ready("hello") else Poll.Pending
            }

        val cx = TaskContext()
        assertTrue(fut.poll(cx) is Poll.Pending)
        val second = fut.poll(cx)
        assertTrue(second is Poll.Ready)
        assertEquals("hello", second.value)
    }
}
