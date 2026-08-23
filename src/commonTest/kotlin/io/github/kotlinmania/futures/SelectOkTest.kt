// port-lint: source futures-util/src/future/select_ok.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectOkTest {
    @Test
    fun testSelectOkFirstSuccess() {
        val f1 = err<Int, String>("err1")
        val f2 = ok<Int, String>(42)
        val f3 = pending<Try<Int, String>>()

        val sel = selectOk(listOf(f1, f2, f3))
        val res = sel.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        val resVal = res.value
        assertTrue(resVal is Try.Ok)
        val (value, rest) = resVal.value
        assertEquals(42, value)
        assertEquals(1, rest.size)
    }

    @Test
    fun testSelectOkAllErrors() {
        val f1 = err<Int, String>("err1")
        val f2 = err<Int, String>("err2")

        val sel = selectOk(listOf(f1, f2))
        val res = sel.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        val resVal = res.value
        assertTrue(resVal is Try.Err)
        assertEquals("err2", resVal.error)
    }
}
