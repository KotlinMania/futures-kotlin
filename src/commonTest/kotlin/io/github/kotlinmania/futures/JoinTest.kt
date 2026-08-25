// port-lint: tests futures-util/src/future/join.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JoinTest {
    @Test
    fun testJoin() {
        val f1 = ready(10)
        val f2 = ready("hello")
        val joined = join(f1, f2)
        val res = joined.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        assertEquals(Pair(10, "hello"), res.value)
    }

    @Test
    fun testJoin3() {
        val f1 = ready(10)
        val f2 = ready("hello")
        val f3 = ready(true)
        val joined = join3(f1, f2, f3)
        val res = joined.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        assertEquals(Triple(10, "hello", true), res.value)
    }

    @Test
    fun testJoin4() {
        val f1 = ready(1)
        val f2 = ready(2)
        val f3 = ready(3)
        val f4 = ready(4)
        val joined = join4(f1, f2, f3, f4)
        val res = joined.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        assertEquals(Tuple4(1, 2, 3, 4), res.value)
    }

    @Test
    fun testJoin5() {
        val f1 = ready(1)
        val f2 = ready(2)
        val f3 = ready(3)
        val f4 = ready(4)
        val f5 = ready(5)
        val joined = join5(f1, f2, f3, f4, f5)
        val res = joined.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        assertEquals(Tuple5(1, 2, 3, 4, 5), res.value)
    }
}
