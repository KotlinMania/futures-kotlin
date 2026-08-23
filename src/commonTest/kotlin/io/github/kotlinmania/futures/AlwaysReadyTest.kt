// port-lint: source futures-util/src/future/always_ready.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlwaysReadyTest {
    @Test
    fun testAlwaysReady() {
        var count = 0
        val fut = alwaysReady { ++count }

        assertFalse(fut.isTerminated())
        val res1 = fut.poll(TaskContext())
        assertTrue(res1 is Poll.Ready)
        assertEquals(1, res1.value)

        assertFalse(fut.isTerminated())
        val res2 = fut.poll(TaskContext())
        assertTrue(res2 is Poll.Ready)
        assertEquals(2, res2.value)
    }
}
