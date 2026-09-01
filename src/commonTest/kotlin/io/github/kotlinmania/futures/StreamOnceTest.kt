// port-lint: tests stream/once.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamOnceTest {
    @Test
    fun testOnceStream() {
        val st = streamOnce(ready(17))
        val cx = TaskContext()

        val p1 = st.pollNext(cx)
        assertTrue(p1 is Poll.Ready)
        assertEquals(Yield.value(17), p1.value)

        val p2 = st.pollNext(cx)
        assertTrue(p2 is Poll.Ready)
        assertEquals(Yield.end(), p2.value)
        assertTrue(st.isTerminated())
    }
}
