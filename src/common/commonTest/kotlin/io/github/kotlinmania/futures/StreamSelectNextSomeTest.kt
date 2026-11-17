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

    @Test
    fun isTerminated() {
        var wakeCount = 0
        val cx = TaskContext(Waker { wakeCount++ })

        val tasks = FuturesUnordered<Int>()

        val selectNextSome1 = tasks.selectNextSome()
        assertFalse(selectNextSome1.isTerminated())
        assertEquals(Poll.pending(), selectNextSome1.poll(cx))
        assertEquals(1, wakeCount)
        assertTrue(selectNextSome1.isTerminated())

        tasks.push(ready(1))

        val selectNextSome2 = tasks.selectNextSome()
        assertFalse(selectNextSome2.isTerminated())
        assertEquals(Poll.ready(1), selectNextSome2.poll(cx))
        assertFalse(selectNextSome2.isTerminated())
        assertEquals(Poll.pending(), selectNextSome2.poll(cx))
        assertTrue(selectNextSome2.isTerminated())
    }

    @Test
    fun select() {
        var fut: Future<Int>? = ready(1).pendingOnce()
        val asyncTasks = FuturesUnordered<Int>()
        var total = 0
        val cx = TaskContext()

        while (fut != null || !asyncTasks.isEmpty()) {
            if (fut != null) {
                val p = fut.poll(cx)
                if (p is Poll.Ready) {
                    total += p.value
                    asyncTasks.push(ready(5))
                    fut = null
                }
            }
            if (!asyncTasks.isEmpty()) {
                val selectNext = asyncTasks.selectNextSome()
                val pNext = selectNext.poll(cx)
                if (pNext is Poll.Ready) {
                    total += pNext.value
                }
            }
        }
        assertEquals(6, total)
    }

    @Test
    fun futuresUtilSelect() {
        select()
    }
}
