// port-lint: tests futures/tests/future_inspect.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FutureInspectTest {
    @Test
    fun smoke() {
        var counter = 0
        val work = ready(40).inspect { counter += it }
        val res = work.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        assertEquals(40, res.value)
        assertEquals(40, counter)
    }
}
