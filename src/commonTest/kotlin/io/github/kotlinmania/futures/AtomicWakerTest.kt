// port-lint: tests futures/tests/task_atomic_waker.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtomicWakerTest {
    @Test
    fun registerAndWake() {
        val atomicWaker = AtomicWaker()
        var woke = false
        val waker = Waker { woke = true }

        atomicWaker.register(waker)
        assertFalse(woke)

        atomicWaker.wake()
        assertTrue(woke)
    }

    @Test
    fun takeReturnsRegisteredWaker() {
        val atomicWaker = AtomicWaker()
        var count = 0
        val waker = Waker { count += 1 }

        assertNull(atomicWaker.take())

        atomicWaker.register(waker)
        val taken = atomicWaker.take()
        assertNotNull(taken)
        assertEquals(0, count)

        taken.wakeByRef()
        assertEquals(1, count)

        assertNull(atomicWaker.take())
    }

    @Test
    fun reregisterOverwritesPreviousWaker() {
        val atomicWaker = AtomicWaker()
        var woke1 = false
        var woke2 = false
        val waker1 = Waker { woke1 = true }
        val waker2 = Waker { woke2 = true }

        atomicWaker.register(waker1)
        atomicWaker.register(waker2)

        atomicWaker.wake()
        assertFalse(woke1)
        assertTrue(woke2)
    }

    @Test
    fun defaultAndToString() {
        val waker = AtomicWaker.default()
        assertEquals("AtomicWaker", waker.toString())
        assertNull(waker.take())
    }
}
