// port-lint: tests stream/empty.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamEmptyTest {
    @Test
    fun testEmptyStream() {
        val st = emptyStream<Int>()
        val cx = TaskContext()

        val p1 = st.pollNext(cx)
        assertTrue(p1 is Poll.Ready)
        assertEquals(Yield.end(), p1.value)
        assertTrue(st.isTerminated())
    }
}
