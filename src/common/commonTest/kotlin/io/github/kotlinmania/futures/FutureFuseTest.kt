// port-lint: tests futures/tests/future_fuse.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FutureFuseTest {
    @Test
    fun testFuse() {
        val future = ready(2).fuse()
        val cx = TaskContext()
        val p1 = future.poll(cx)
        assertTrue(p1 is Poll.Ready)
        assertEquals(2, p1.value)

        val p2 = future.poll(cx)
        assertTrue(p2 is Poll.Pending)
    }
}
