// port-lint: tests stream/pending.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertTrue

class StreamPendingTest {
    @Test
    fun testPendingStream() {
        val st = pendingStream<Int>()
        val cx = TaskContext()

        val p1 = st.pollNext(cx)
        assertTrue(p1 is Poll.Pending)
    }
}
