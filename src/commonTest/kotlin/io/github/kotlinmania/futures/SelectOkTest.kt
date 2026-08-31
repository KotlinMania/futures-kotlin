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

    @Test
    fun ignoreErr() {
        val v = listOf(err<Int, Int>(1), err<Int, Int>(2), ok<Int, Int>(3), ok<Int, Int>(4))
        val cx = TaskContext()

        val p1 = selectOk(v).poll(cx)
        val r1 = ((p1 as Poll.Ready).value as Try.Ok).value
        assertEquals(3, r1.first)
        assertEquals(1, r1.second.size)

        val p2 = selectOk(r1.second).poll(cx)
        val r2 = ((p2 as Poll.Ready).value as Try.Ok).value
        assertEquals(4, r2.first)
        assertTrue(r2.second.isEmpty())
    }

    @Test
    fun lastErr() {
        val v = listOf(ok<Int, Int>(1), err<Int, Int>(2), err<Int, Int>(3))
        val cx = TaskContext()

        val p1 = selectOk(v).poll(cx)
        val r1 = ((p1 as Poll.Ready).value as Try.Ok).value
        assertEquals(1, r1.first)
        assertEquals(2, r1.second.size)

        val p2 = selectOk(r1.second).poll(cx)
        val r2 = ((p2 as Poll.Ready).value as Try.Err).error
        assertEquals(3, r2)
    }
}
