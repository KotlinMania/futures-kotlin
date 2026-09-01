// port-lint: source future/try_select.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrySelectTest {
    @Test
    fun testTrySelectLeftOk() {
        val f1 = ok<Int, String>(10)
        val f2 = pending<Try<String, String>>()

        val sel = trySelect(f1, f2)
        val res = sel.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        val resVal = res.value
        assertTrue(resVal is Try.Ok)
        val okVal = resVal.value
        assertTrue(okVal is Either.Left)
        assertEquals(10, okVal.value.first)
    }

    @Test
    fun testTrySelectRightOk() {
        val f1 = pending<Try<Int, String>>()
        val f2 = ok<String, String>("world")

        val sel = trySelect(f1, f2)
        val res = sel.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        val resVal = res.value
        assertTrue(resVal is Try.Ok)
        val okVal = resVal.value
        assertTrue(okVal is Either.Right)
        assertEquals("world", okVal.value.first)
    }

    @Test
    fun testTrySelectLeftErr() {
        val f1 = err<Int, String>("err1")
        val f2 = pending<Try<String, String>>()

        val sel = trySelect(f1, f2)
        val res = sel.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        val resVal = res.value
        assertTrue(resVal is Try.Err)
        val errVal = resVal.error
        assertTrue(errVal is Either.Left)
        assertEquals("err1", errVal.value.first)
    }
}
