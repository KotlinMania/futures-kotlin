// port-lint: tests stream/iter.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamIterTest {
    @Test
    fun testIterStream() {
        val st = streamIter(listOf(17, 19))
        val cx = TaskContext()

        val p1 = st.pollNext(cx)
        assertTrue(p1 is Poll.Ready)
        assertEquals(Yield.value(17), p1.value)

        val p2 = st.pollNext(cx)
        assertTrue(p2 is Poll.Ready)
        assertEquals(Yield.value(19), p2.value)

        val p3 = st.pollNext(cx)
        assertTrue(p3 is Poll.Ready)
        assertEquals(Yield.end(), p3.value)
    }
}
