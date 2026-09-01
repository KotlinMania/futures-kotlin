// port-lint: source future/maybe_done.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaybeDoneTest {
    @Test
    fun testMaybeDoneLifecycle() {
        val cx = TaskContext()
        val md = maybeDone(ready(42))

        assertFalse(md.isTerminated())
        assertNull(md.outputOrNull())

        val pollRes = md.poll(cx)
        assertTrue(pollRes is Poll.Ready)
        assertTrue(md.isTerminated())
        assertEquals(42, md.outputOrNull())

        val value = md.takeOutput()
        assertEquals(42, value)
        assertNull(md.outputOrNull())
        assertNull(md.takeOutput())
        assertTrue(md.isTerminated())
    }

    @Test
    fun testMaybeDonePending() {
        val cx = TaskContext()
        val md = maybeDone(pending<Int>())

        assertFalse(md.isTerminated())
        val pollRes = md.poll(cx)
        assertTrue(pollRes is Poll.Pending)
        assertFalse(md.isTerminated())
        assertNull(md.outputOrNull())
        assertNull(md.takeOutput())
    }
}
