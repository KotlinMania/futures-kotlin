// port-lint: tests future_try_flatten_stream.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TryFutureCombinatorsAdvancedTest {
    @Test
    fun testIntoFuture() {
        val tryFut: TryFuture<Int, String> = ready(Try.ok(100)).asTryFuture()
        val stdFut: Future<Try<Int, String>> = tryFut.intoFuture()
        val context = TaskContext()
        val res = stdFut.poll(context)
        assertTrue(res is Poll.Ready)
        assertEquals(Try.ok(100), res.value)
    }

    @Test
    fun testTryFlattenErrFirstOk() {
        val outer: TryFuture<Int, TryFuture<Int, String>> = ready(Try.ok(42)).asTryFuture()
        val flattened = outer.tryFlattenErr()
        val context = TaskContext()
        val res = flattened.poll(context)
        assertTrue(res is Poll.Ready)
        assertEquals(Try.ok(42), res.value)
    }

    @Test
    fun testTryFlattenErrRecovered() {
        val inner: TryFuture<Int, String> = ready(Try.ok(99)).asTryFuture()
        val outer: TryFuture<Int, TryFuture<Int, String>> = ready(Try.err(inner)).asTryFuture()
        val flattened = outer.tryFlattenErr()
        val context = TaskContext()
        val res = flattened.poll(context)
        assertTrue(res is Poll.Ready)
        assertEquals(Try.ok(99), res.value)
    }

    @Test
    fun testTryFlattenErrSecondError() {
        val inner: TryFuture<Int, String> = ready(Try.err("second fail")).asTryFuture()
        val outer: TryFuture<Int, TryFuture<Int, String>> = ready(Try.err(inner)).asTryFuture()
        val flattened = outer.tryFlattenErr()
        val context = TaskContext()
        val res = flattened.poll(context)
        assertTrue(res is Poll.Ready)
        assertEquals(Try.err("second fail"), res.value)
    }
}
