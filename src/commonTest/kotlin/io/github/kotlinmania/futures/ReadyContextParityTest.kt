package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadyContextParityTest {
    private class Probe : Waker {
        var wakes = 0
        var hashes = 0
        var failOnHash = false

        override fun wakeByRef() {
            wakes += 1
        }

        override fun hashCode(): Int {
            hashes += 1
            check(!failOnHash) { "hash hook" }
            return 17
        }
    }

    @Test
    fun readyValueDoesNotUseWaker() {
        val probe = Probe()
        val future = ready(42)
        assertFalse(future.isTerminated())
        assertEquals(Poll.Ready(42), future.poll(TaskContext(probe)))
        assertTrue(future.isTerminated())
        assertEquals(0, probe.wakes)
        assertEquals(0, probe.hashes)
    }

    @Test
    fun nullableValueRemainsAReadyValue() {
        val probe = Probe()
        val future = ready<Int?>(null)
        assertFalse(future.isTerminated())
        assertEquals(Poll.Ready<Int?>(null), future.poll(TaskContext(probe)))
        assertTrue(future.isTerminated())
        assertEquals(0, probe.wakes)
        assertEquals(0, probe.hashes)
    }

    @Test
    fun successAndErrorPayloadsDoNotUseWaker() {
        val probe = Probe()
        val success = ok<Int?, String?>(null)
        val failure = err<Int?, String?>(null)
        assertFalse(success.isTerminated())
        assertFalse(failure.isTerminated())
        assertEquals(Poll.Ready(Try.Ok<Int?>(null)), success.poll(TaskContext(probe)))
        assertEquals(Poll.Ready(Try.Err<String?>(null)), failure.poll(TaskContext(probe)))
        assertTrue(success.isTerminated())
        assertTrue(failure.isTerminated())
        assertEquals(0, probe.wakes)
        assertEquals(0, probe.hashes)
    }

    @Test
    fun completedReadyHasExactGuard() {
        val probe = Probe()
        val future = ready(7)
        assertEquals(Poll.Ready(7), future.poll(TaskContext(probe)))
        val failure = assertFailsWith<IllegalStateException> { future.poll(TaskContext(probe)) }
        assertEquals("Ready polled after completion", failure.message)
        assertTrue(future.isTerminated())
        assertEquals(0, probe.wakes)
        assertEquals(0, probe.hashes)
    }

    // Kotlin-specific adaptation checks: Rust Waker has no corresponding user hash hook.
    @Test
    fun throwingHashDoesNotBlockReady() {
        val probe = Probe().also { it.failOnHash = true }
        val future = ready(9)
        assertEquals(Poll.Ready(9), future.poll(TaskContext(probe)))
        assertTrue(future.isTerminated())
        assertEquals(0, probe.wakes)
        assertEquals(0, probe.hashes)
    }

    @Test
    fun throwingHashDoesNotMaskCompletionGuard() {
        val probe = Probe()
        val future = ready(9)
        assertEquals(Poll.Ready(9), future.poll(TaskContext(probe)))
        probe.failOnHash = true
        val failure = assertFailsWith<IllegalStateException> { future.poll(TaskContext(probe)) }
        assertEquals("Ready polled after completion", failure.message)
        assertTrue(future.isTerminated())
        assertEquals(0, probe.wakes)
        assertEquals(0, probe.hashes)
    }
}
