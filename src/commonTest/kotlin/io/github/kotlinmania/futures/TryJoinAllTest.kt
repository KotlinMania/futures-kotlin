// port-lint: source futures-util/src/future/try_join_all.rs
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
}
