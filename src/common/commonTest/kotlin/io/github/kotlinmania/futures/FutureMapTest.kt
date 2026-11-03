// port-lint: tests future/future/map.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FutureMapTest {
    @Test
    fun testMap() {
        val f = ready(21).map { it * 2 }
        assertFalse(f.isTerminated())
        val p = f.poll(TaskContext())
        assertTrue(p is Poll.Ready)
        assertEquals(42, p.value)
        assertTrue(f.isTerminated())
        assertFailsWith<IllegalStateException> {
            f.poll(TaskContext())
        }
    }
}
