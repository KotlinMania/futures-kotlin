// port-lint: tests future/either.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EitherTest {
    @Test
    fun testEitherConstructorsAndProperties() {
        val left: Either<Int, String> = Either.left(42)
        val right: Either<Int, String> = Either.right("hello")

        assertTrue(left.isLeft)
        assertFalse(left.isRight)
        assertEquals(42, left.leftOrNull())
        assertNull(left.rightOrNull())

        assertFalse(right.isLeft)
        assertTrue(right.isRight)
        assertNull(right.leftOrNull())
        assertEquals("hello", right.rightOrNull())

        val foldedLeft =
            left.fold(
                onLeft = { it * 2 },
                onRight = { 0 },
            )
        assertEquals(84, foldedLeft)

        val foldedRight =
            right.fold(
                onLeft = { "" },
                onRight = { it.uppercase() },
            )
        assertEquals("HELLO", foldedRight)
    }

    @Test
    fun testIntoInnerAndFactor() {
        val leftHomogeneous: Either<String, String> = Either.left("leftVal")
        val rightHomogeneous: Either<String, String> = Either.right("rightVal")

        assertEquals("leftVal", leftHomogeneous.intoInner())
        assertEquals("rightVal", rightHomogeneous.intoInner())

        val pairLeft: Either<Pair<Int, String>, Pair<Int, Boolean>> = Either.left(Pair(1, "a"))
        val factoredFirst = pairLeft.factorFirst()
        assertEquals(1, factoredFirst.first)
        assertEquals(Either.left("a"), factoredFirst.second)

        val pairRight: Either<Pair<String, Int>, Pair<Boolean, Int>> = Either.right(Pair(true, 10))
        val factoredSecond = pairRight.factorSecond()
        assertEquals(Either.right(true), factoredSecond.first)
        assertEquals(10, factoredSecond.second)
    }

    @Test
    fun testEitherFuture() {
        val fLeft: Either<Future<Int>, Future<Int>> = Either.left(ready(100))
        val combinedLeft = fLeft.asFuture()
        val resLeft = combinedLeft.poll(TaskContext())
        assertTrue(resLeft is Poll.Ready<*>)
        assertEquals(100, (resLeft as Poll.Ready<Int>).value)
        assertTrue(combinedLeft.isTerminated())

        val fRight: Either<Future<Int>, Future<Int>> = Either.right(ready(200))
        val combinedRight = fRight.asFuture()
        val resRight = combinedRight.poll(TaskContext())
        assertTrue(resRight is Poll.Ready<*>)
        assertEquals(200, (resRight as Poll.Ready<Int>).value)
        assertTrue(combinedRight.isTerminated())
    }

    @Test
    fun testEitherSink() {
        val list = mutableListOf<Int>()
        val sink: Sink<Int, Nothing> = list.asSink()
        val eitherSink: Either<Sink<Int, Nothing>, Sink<Int, Nothing>> = Either.left(sink)
        val combined = eitherSink.asSink()

        val readyRes = combined.pollReady(TaskContext())
        assertTrue(readyRes is Poll.Ready<*>)
        combined.startSend(42)
        assertEquals(listOf(42), list)
    }
}
