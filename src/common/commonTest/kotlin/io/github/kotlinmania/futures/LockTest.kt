// port-lint: tests futures-channel/src/lock.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LockTest {
    @Test
    fun smoke() {
        val a = Lock.new(1)
        val a1 = a.tryLock()
        assertNotNull(a1)
        assertNull(a.tryLock())
        assertEquals(1, a1.get())
        a1.set(2)
        a1.unlock()
        val a2 = a.tryLock()
        assertNotNull(a2)
        assertEquals(2, a2.get())
        a2.unlock()
        val a3 = a.tryLock()
        assertNotNull(a3)
        assertEquals(2, a3.get())
    }
}
