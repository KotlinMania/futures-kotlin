// port-lint: tests futures-util/src/stream/repeat_with.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamRepeatWithTest {
    @Test
    fun testRepeatWithStream() {
        var count = 1
        val st = streamRepeatWith { count++ }
        val cx = TaskContext()

        val p1 = st.pollNext(cx)
        assertTrue(p1 is Poll.Ready)
        assertEquals(Yield.value(1), p1.value)

        val p2 = st.pollNext(cx)
        assertTrue(p2 is Poll.Ready)
        assertEquals(Yield.value(2), p2.value)

        val p3 = st.pollNext(cx)
        assertTrue(p3 is Poll.Ready)
        assertEquals(Yield.value(3), p3.value)
    }
}
