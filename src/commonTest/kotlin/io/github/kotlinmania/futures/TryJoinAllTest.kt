// port-lint: source future/try_join_all.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TryJoinAllTest {
    @Test
    fun testTryJoinAllSuccess() {
        val list = listOf(ok<Int, String>(1), ok<Int, String>(2), ok<Int, String>(3))
        val joined = tryJoinAll(list)
        val res = joined.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        val resVal = res.value
        assertTrue(resVal is Try.Ok)
        assertEquals(listOf(1, 2, 3), resVal.value)
    }

    @Test
    fun testTryJoinAllError() {
        val list = listOf(ok<Int, String>(1), err<Int, String>("err2"), ok<Int, String>(3))
        val joined = tryJoinAll(list)
        val res = joined.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        val resVal = res.value
        assertTrue(resVal is Try.Err)
        assertEquals("err2", resVal.error)
    }

    @Test
    fun collectCollects() {
        val cx = TaskContext()
        val res1 = tryJoinAll(listOf(ok<Int, Int>(1), ok<Int, Int>(2))).poll(cx)
        assertEquals(Poll.ready(Try.ok(listOf(1, 2))), res1)

        val res2 = tryJoinAll(listOf(ok<Int, Int>(1), err<Int, Int>(2))).poll(cx)
        assertEquals(Poll.ready(Try.err(2)), res2)

        val res3 = tryJoinAll(listOf(ok<Int, Int>(1))).poll(cx)
        assertEquals(Poll.ready(Try.ok(listOf(1))), res3)
    }

    @Test
    fun tryJoinAllSizes() {
        val bufs = listOf(byteArrayOf(1, 2, 3), byteArrayOf(), byteArrayOf(0))
        val iter = bufs.map { ok<Int, Unit>(it.size) }
        val cx = TaskContext()
        val res = tryJoinAll(iter).poll(cx)
        assertEquals(Poll.ready(Try.ok(listOf(3, 0, 1))), res)
    }
}
