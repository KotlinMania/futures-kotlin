// port-lint: tests futures-util/tests/select.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectTest {
    @Test
    fun testSelectFirstWins() {
        val f1 = ready(10)
        val f2 = pending<String>()
        val sel = select(f1, f2)

        val res = sel.poll(TaskContext())
        assertTrue(res is Poll.Ready<*>)
        val either = (res as Poll.Ready<Either<Pair<Int, Future<String>>, Pair<String, Future<Int>>>>).value
        assertTrue(either is Either.Left)
        assertEquals(10, either.value.first)
    }

    @Test
    fun testSelectSecondWins() {
        val f1 = pending<Int>()
        val f2 = ready("second")
        val sel = select(f1, f2)

        val res = sel.poll(TaskContext())
        assertTrue(res is Poll.Ready<*>)
        val either = (res as Poll.Ready<Either<Pair<Int, Future<String>>, Pair<String, Future<Int>>>>).value
        assertTrue(either is Either.Right)
        assertEquals("second", either.value.first)
    }

    @Test
    fun testSelectValues() {
        val f1 = ready(99)
        val f2 = pending<String>()
        val sel = selectValues(f1, f2)

        val res = sel.poll(TaskContext())
        assertTrue(res is Poll.Ready<*>)
        val either = (res as Poll.Ready<Either<Int, String>>).value
        assertTrue(either is Either.Left)
        assertEquals(99, either.value)
    }
}
