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

    @Test
    fun collectCollects() {
        val cx = TaskContext()
        val res1 = joinAll(listOf(ready(1), ready(2))).poll(cx)
        assertEquals(Poll.ready(listOf(1, 2)), res1)

        val res2 = joinAll(listOf(ready(1))).poll(cx)
        assertEquals(Poll.ready(listOf(1)), res2)
    }

    @Test
    fun joinAllSizes() {
        val bufs = listOf(byteArrayOf(1, 2, 3), byteArrayOf(), byteArrayOf(0))
        val iter = bufs.map { ready(it.size) }
        val cx = TaskContext()
        val res = joinAll(iter).poll(cx)
        assertEquals(Poll.ready(listOf(3, 0, 1)), res)
    }
}
