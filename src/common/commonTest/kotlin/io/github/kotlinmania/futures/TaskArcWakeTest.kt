// port-lint: tests futures/tests/task_arc_wake.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals

class CountingWaker : ArcWake {
    var nrWake: Int = 0

    fun wakes(): Int = nrWake

    override fun wakeByRef() {
        nrWake += 1
    }
}

class TaskArcWakeTest {
    @Test
    fun createFromArc() {
        val someW = CountingWaker()

        val w1: Waker = waker(someW)
        w1.wakeByRef()
        assertEquals(1, someW.wakes())

        val w2 = w1
        w2.wakeByRef()
        assertEquals(2, someW.wakes())
    }

    @Test
    fun refWakeSame() {
        val someW = CountingWaker()

        val w1: Waker = waker(someW)
        val w2 = wakerRef(someW)

        w1.wakeByRef()
        w2.wakeByRef()
        assertEquals(2, someW.wakes())
    }
}
