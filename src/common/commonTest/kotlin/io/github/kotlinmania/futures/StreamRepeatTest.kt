// port-lint: tests stream/repeat.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamRepeatTest {
    @Test
    fun testRepeatStream() {
        val st = streamRepeat(9)
        val cx = TaskContext()

        for (i in 0 until 5) {
            val p = st.pollNext(cx)
            assertTrue(p is Poll.Ready)
            assertEquals(Yield.value(9), p.value)
        }
    }
}
