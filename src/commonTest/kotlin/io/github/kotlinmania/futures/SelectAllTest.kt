// port-lint: source futures-util/src/future/select_all.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectAllTest {
    @Test
    fun testSelectAll() {
        val f1 = pending<Int>()
        val f2 = ready(42)
        val f3 = pending<Int>()

        val select = selectAll(listOf(f1, f2, f3))
        val res = select.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        val (value, idx, rest) = res.value
        assertEquals(42, value)
        assertEquals(1, idx)
        assertEquals(2, rest.size)
    }
}
