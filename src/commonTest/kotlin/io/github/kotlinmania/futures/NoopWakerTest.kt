// port-lint: tests futures-task/src/noop_waker.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertSame

class NoopWakerTest {
    @Test
    fun noopWakerIsCallable() {
        val waker = noopWaker()
        waker.wakeByRef()
        waker.wakeByRef()
    }

    @Test
    fun noopWakerRefIsCallable() {
        val waker = noopWakerRef()
        waker.wakeByRef()
    }

    @Test
    fun noopWakerRefIsShared() {
        val a = noopWakerRef()
        val b = noopWakerRef()
        assertSame(a, b)
    }

    @Test
    fun issue2091CrossThreadSegfault() {
        val waker = noopWakerRef()
        waker.wakeByRef()
    }
}
