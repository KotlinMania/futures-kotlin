// port-lint: tests futures/tests/future_obj.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FutureObjTest {
    @Test
    fun droppingDoesNotSegfault() {
        val obj = FutureObj.new(Future { Poll.Ready("test") })
        val local = obj.intoLocalFutureObj()
        val back = local.intoFutureObj()
        val res = back.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        assertEquals("test", res.value)
    }

    @Test
    fun localFutureObjPolling() {
        val local = LocalFutureObj.new(Future { Poll.Ready(42) })
        val res = local.poll(TaskContext())
        assertTrue(res is Poll.Ready)
        assertEquals(42, res.value)
    }
}
