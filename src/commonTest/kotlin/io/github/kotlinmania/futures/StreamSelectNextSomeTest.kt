// port-lint: tests futures/tests/stream_select_next_some.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamSelectNextSomeTest {
    private val context = TaskContext()

    @Test
    fun testIsTerminated() {
        val tasks = FuturesUnordered<Int>()

        val selectNextSome1 = tasks.selectNextSome()
        assertFalse(selectNextSome1.isTerminated())
        assertEquals(Poll.Pending, selectNextSome1.poll(context))
        assertTrue(selectNextSome1.isTerminated())

        tasks.push(Ready.new(1))

        val selectNextSome2 = tasks.selectNextSome()
        assertFalse(selectNextSome2.isTerminated())
        assertEquals(Poll.Ready(1), selectNextSome2.poll(context))
        assertFalse(selectNextSome2.isTerminated())
        assertEquals(Poll.Pending, selectNextSome2.poll(context))
        assertTrue(selectNextSome2.isTerminated())
    }
}
