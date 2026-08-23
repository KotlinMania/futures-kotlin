// port-lint: tests futures-util/tests/option.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OptionFutureTest {
    @Test
    fun testSomeFuture() {
        val f: Future<Int>? = ready(42)
        val optFuture = f.intoOptionFuture()
        val res = optFuture.poll(TaskContext())
        assertTrue(res is Poll.Ready<*>)
        assertEquals(42, (res as Poll.Ready<Int?>).value)
        assertTrue(optFuture.isTerminated())
    }

    @Test
    fun testNoneFuture() {
        val f: Future<Int>? = null
        val optFuture = f.intoOptionFuture()
        assertTrue(optFuture.isTerminated())
        val res = optFuture.poll(TaskContext())
        assertTrue(res is Poll.Ready<*>)
        assertNull((res as Poll.Ready<Int?>).value)
    }
}
