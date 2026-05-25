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

    // Upstream issue_2091_cross_thread_segfault verifies that the `&'static
    // Waker` produced by `noop_waker_ref` survives transfer between OS
    // threads via raw-pointer casts through `RawWaker`. Kotlin has no
    // analogue: the Waker SAM holds an ordinary heap reference, so a
    // cross-thread send is just a normal reference share. There is no
    // segfault scenario to reproduce.
}
