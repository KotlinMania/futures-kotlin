// port-lint: tests futures/tests/bilock.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BiLockTest {
    @Test
    fun smoke() {
        val (a, b) = BiLock.new(1)
        val cx = TaskContext()

        val lockA = a.pollLock(cx)
        assertIs<Poll.Ready<BiLockGuard<Int>>>(lockA)
        val guardA = lockA.value
        assertEquals(1, guardA.get())
        guardA.set(2)

        assertTrue(b.pollLock(cx) is Poll.Pending)
        assertTrue(a.pollLock(cx) is Poll.Pending)

        guardA.unlock()

        val lockB1 = b.pollLock(cx)
        assertIs<Poll.Ready<BiLockGuard<Int>>>(lockB1)
        lockB1.value.unlock()

        val lockA1 = a.pollLock(cx)
        assertIs<Poll.Ready<BiLockGuard<Int>>>(lockA1)
        lockA1.value.unlock()

        val lockB = b.pollLock(cx)
        assertIs<Poll.Ready<BiLockGuard<Int>>>(lockB)
        val guardB = lockB.value
        assertEquals(2, guardB.get())
        guardB.unlock()

        val reunited = a.reunite(b)
        assertTrue(reunited.isSuccess)
        assertEquals(2, reunited.getOrThrow())
    }

    @Test
    fun isPairOfMatchesExpected() {
        val (a1, b1) = BiLock.new(10)
        val (a2, b2) = BiLock.new(20)

        assertTrue(a1.isPairOf(b1))
        assertTrue(b1.isPairOf(a1))
        assertFalse(a1.isPairOf(a2))
        assertFalse(a1.isPairOf(b2))

        val failReunite = a1.reunite(b2)
        assertTrue(failReunite.isFailure)
        assertIs<BiLockReuniteError>(failReunite.exceptionOrNull())
    }

    @Test
    fun lockFutureResolves() {
        val (a, b) = BiLock.new("hello")
        val cx = TaskContext()
        val future = a.lock()

        val poll1 = future.poll(cx)
        assertIs<Poll.Ready<BiLockGuard<String>>>(poll1)
        val guard = poll1.value
        assertEquals("hello", guard.get())
        guard.set("world")
        guard.unlock()

        val (first, _) = a.reunite(b).getOrThrow() to b
        assertEquals("world", first)
    }

    @Test
    fun guardWithValueExecutesAndUnlocks() {
        val (a, b) = BiLock.new(42)
        val cx = TaskContext()

        val lockPoll = a.pollLock(cx)
        assertIs<Poll.Ready<BiLockGuard<Int>>>(lockPoll)
        val res = lockPoll.value.withValue { it * 2 }
        assertEquals(84, res)

        // Lock should be unlocked now
        val testLock = b.pollLock(cx)
        assertTrue(testLock is Poll.Ready)
        testLock.value.unlock()
    }
}
