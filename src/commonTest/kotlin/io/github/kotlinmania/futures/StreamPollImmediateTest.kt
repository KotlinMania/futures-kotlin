// port-lint: tests futures-util/src/stream/poll_immediate.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamPollImmediateTest {
    @Test
    fun testPollImmediateStream() {
        val base = streamIter(listOf(1, 2))
        val st = streamPollImmediate(base)
        val cx = TaskContext()

        val p1 = st.pollNext(cx)
        assertTrue(p1 is Poll.Ready)
        assertEquals(Yield.value(Poll.ready(1)), p1.value)

        val p2 = st.pollNext(cx)
        assertTrue(p2 is Poll.Ready)
        assertEquals(Yield.value(Poll.ready(2)), p2.value)

        val p3 = st.pollNext(cx)
        assertTrue(p3 is Poll.Ready)
        assertEquals(Yield.end(), p3.value)
    }
}
