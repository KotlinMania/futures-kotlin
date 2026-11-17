// port-lint: tests futures-util/src/fns.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FnsTest {
    @Test
    fun testOkFn() {
        val f = okFn<String>()
        val res = f(123)
        assertTrue(res is Try.Ok)
        assertEquals(123, res.value)
    }

    @Test
    fun testChainFn() {
        val f = chainFn({ x: Int -> x + 1 }, { y: Int -> y * 2 })
        assertEquals(10, f(4))
    }

    @Test
    fun testMergeResultFn() {
        val f = mergeResultFn<Int>()
        assertEquals(10, f(Try.ok(10)))
        assertEquals(20, f(Try.err(20)))
    }

    @Test
    fun testInspectFn() {
        var observed = 0
        val f = inspectFn<Int> { observed = it }
        val ret = f(42)
        assertEquals(42, ret)
        assertEquals(42, observed)
    }

    @Test
    fun testMapOkAndMapErrFn() {
        val mapOk = mapOkFn<Int, String, String> { "val:$it" }
        assertEquals(Try.ok("val:5"), mapOk(Try.ok(5)))
        assertEquals(Try.err("err"), mapOk(Try.err("err")))

        val mapErr = mapErrFn<Int, String, String> { "mapped:$it" }
        assertEquals(Try.ok(5), mapErr(Try.ok(5)))
        assertEquals(Try.err("mapped:err"), mapErr(Try.err("err")))
    }

    @Test
    fun testUnwrapOrElseFn() {
        val f = unwrapOrElseFn<Int, String> { it.length }
        assertEquals(100, f(Try.ok(100)))
        assertEquals(5, f(Try.err("hello")))
    }
}
