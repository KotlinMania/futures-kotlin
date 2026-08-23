// port-lint: source futures-util/src/abortable.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbortableTest {
    @Test
    fun testAbortableCompletesWithoutAbort() {
        val (fut, handle) = abortable(ready(42))
        assertFalse(handle.isAborted())
        val res = fut.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        val v = res.value
        assertTrue(v is Try.Ok)
        assertEquals(42, v.value)
        assertFalse(handle.isAborted())
    }

    @Test
    fun testAbortableAborts() {
        val (fut, handle) = abortable(pending<Int>())
        assertFalse(handle.isAborted())

        val p1 = fut.poll(TaskContext())
        assertTrue(p1 is Poll.Pending)

        handle.abort()
        assertTrue(handle.isAborted())

        val p2 = fut.poll(TaskContext())
        assertTrue(p2 is Poll.Ready)
        val v = p2.value
        assertTrue(v is Try.Err)
        assertEquals(Aborted, v.error)
    }
}
