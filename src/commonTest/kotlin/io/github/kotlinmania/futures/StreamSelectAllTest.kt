// port-lint: tests futures-util/src/stream/select_all.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamSelectAllTest {
    @Test
    fun testSelectAllStream() {
        val s1 = streamIter(listOf(1, 3))
        val s2 = streamIter(listOf(2, 4))
        val select = streamSelectAll(listOf(s1, s2))
        val cx = TaskContext()

        val results = mutableListOf<Int>()
        while (true) {
            val p = select.pollNext(cx)
            assertTrue(p is Poll.Ready)
            when (val y = p.value) {
                is Yield.Value -> results.add(y.value)
                Yield.End -> break
            }
        }
        assertEquals(4, results.size)
        assertTrue(results.containsAll(listOf(1, 2, 3, 4)))
    }
}
