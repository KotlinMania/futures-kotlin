// port-lint: source futures-util/src/future/join_all.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JoinAllTest {
    @Test
    fun testJoinAll() {
        val list = listOf(ready(1), ready(2), ready(3))
        val joined = joinAll(list)
        val res = joined.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        assertEquals(listOf(1, 2, 3), res.value)
    }

    @Test
    fun testJoinAllEmpty() {
        val list = emptyList<Future<Int>>()
        val joined = joinAll(list)
        val res = joined.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        assertEquals(emptyList(), res.value)
    }
}
