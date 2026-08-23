// port-lint: source futures-util/src/future/try_maybe_done.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TryMaybeDoneTest {
    @Test
    fun testTryMaybeDoneSuccess() {
        val cx = TaskContext()
        val md = tryMaybeDone(ok<Int, String>(100))

        assertFalse(md.isTerminated())
        assertNull(md.outputOrNull())

        val pollRes = md.poll(cx)
        assertTrue(pollRes is Poll.Ready)
        val resVal = pollRes.value
        assertTrue(resVal is Try.Ok)
        assertTrue(md.isTerminated())
        assertEquals(100, md.outputOrNull())

        val value = md.takeOutput()
        assertEquals(100, value)
        assertNull(md.outputOrNull())
        assertNull(md.takeOutput())
    }

    @Test
    fun testTryMaybeDoneError() {
        val cx = TaskContext()
        val md = tryMaybeDone(err<Int, String>("failed"))

        assertFalse(md.isTerminated())
        val pollRes = md.poll(cx)
        assertTrue(pollRes is Poll.Ready)
        val resVal = pollRes.value
        assertTrue(resVal is Try.Err)
        assertEquals("failed", resVal.error)
        assertTrue(md.isTerminated())
        assertNull(md.outputOrNull())
        assertNull(md.takeOutput())
    }
}
