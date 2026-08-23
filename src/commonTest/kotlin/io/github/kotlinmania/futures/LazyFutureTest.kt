// port-lint: tests futures-util/tests/lazy.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LazyFutureTest {
    @Test
    fun testLazyExecution() {
        var counter = 0
        val lazy =
            lazyFuture {
                counter++
                "result"
            }

        assertFalse(lazy.isTerminated())
        assertEquals(0, counter)

        val res = lazy.poll(TaskContext())
        assertTrue(res is Poll.Ready<*>)
        assertEquals("result", (res as Poll.Ready<String>).value)
        assertEquals(1, counter)
        assertTrue(lazy.isTerminated())
    }
}
