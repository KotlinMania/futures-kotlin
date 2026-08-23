// port-lint: source futures-util/src/future/ready.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadyPendingTest {
    @Test
    fun testReady() {
        val r = ready(42)
        assertFalse(r.isTerminated())
        val p = r.poll(TaskContext())
        assertTrue(p is Poll.Ready)
        assertEquals(42, p.value)
        assertTrue(r.isTerminated())
    }

    @Test
    fun testReadyIntoInner() {
        val r = ready("hello")
        assertEquals("hello", r.intoInner())
        assertTrue(r.isTerminated())
    }

    @Test
    fun testOkAndErr() {
        val okFut = ok<Int, String>(10)
        val pOk = okFut.poll(TaskContext())
        assertTrue(pOk is Poll.Ready)
        val okVal = pOk.value
        assertTrue(okVal is Try.Ok)
        assertEquals(10, okVal.value)

        val errFut = err<Int, String>("err")
        val pErr = errFut.poll(TaskContext())
        assertTrue(pErr is Poll.Ready)
        val errVal = pErr.value
        assertTrue(errVal is Try.Err)
        assertEquals("err", errVal.error)
    }

    @Test
    fun testPending() {
        val p = pending<Int>()
        assertTrue(p.isTerminated())
        assertTrue(p.poll(TaskContext()) is Poll.Pending)
    }
}
