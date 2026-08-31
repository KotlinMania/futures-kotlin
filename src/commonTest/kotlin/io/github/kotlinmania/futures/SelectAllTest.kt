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

    @Test
    fun smoke() {
        var v: List<Future<Int>> = listOf(ready(1), ready(2), ready(3))
        val c = mutableSetOf(1, 2, 3)

        val cx = TaskContext()

        val r1 = (selectAll(v).poll(cx) as Poll.Ready).value
        assertTrue(c.remove(r1.first))
        assertEquals(0, r1.second)
        v = r1.third

        val r2 = (selectAll(v).poll(cx) as Poll.Ready).value
        assertTrue(c.remove(r2.first))
        assertEquals(0, r2.second)
        v = r2.third

        val r3 = (selectAll(v).poll(cx) as Poll.Ready).value
        assertTrue(c.remove(r3.first))
        assertEquals(0, r3.second)
        v = r3.third

        assertTrue(c.isEmpty())
        assertTrue(v.isEmpty())
    }
}
