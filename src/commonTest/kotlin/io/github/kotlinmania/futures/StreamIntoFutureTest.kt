// port-lint: tests stream/stream/into_future.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamIntoFutureTest {
    @Test
    fun testIntoFuture() {
        val st = streamIter(listOf(1, 2))
        val intoFuture = st.intoFuture()
        val cx = TaskContext()

        assertFalse(intoFuture.isTerminated())
        val p1 = intoFuture.poll(cx)
        assertTrue(p1 is Poll.Ready)
        val (item1, rest1) = p1.value
        assertEquals(1, item1)
        assertTrue(intoFuture.isTerminated())

        val intoFuture2 = rest1.intoFuture()
        val p2 = intoFuture2.poll(cx)
        assertTrue(p2 is Poll.Ready)
        val (item2, rest2) = p2.value
        assertEquals(2, item2)

        val intoFuture3 = rest2.intoFuture()
        val p3 = intoFuture3.poll(cx)
        assertTrue(p3 is Poll.Ready)
        val (item3, _) = p3.value
        assertEquals(null, item3)
    }
}
