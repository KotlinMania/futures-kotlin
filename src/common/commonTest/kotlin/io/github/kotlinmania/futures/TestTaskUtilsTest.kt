// port-lint: tests futures-test/src/task/mod.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestTaskUtilsTest {
    @Test
    fun testWakeCounter() {
        val (waker, count) = newCountWaker()
        assertEquals(0, count.get())

        waker.wakeByRef()
        assertEquals(1, count.get())

        waker.wake()
        assertEquals(2, count.get())
    }

    @Test
    fun testPanicWaker() {
        val waker = panicWaker()
        assertFailsWith<IllegalStateException> {
            waker.wake()
        }

        val wakerRef = panicWakerRef()
        assertFailsWith<IllegalStateException> {
            wakerRef.wakeByRef()
        }
    }

    @Test
    fun testContexts() {
        val noopCx = noopContext()
        noopCx.wakeByRef()

        val panicCx = panicContext()
        assertFailsWith<IllegalStateException> {
            panicCx.wakeByRef()
        }
    }

    @Test
    fun testNoopSpawner() {
        val spawner = NoopSpawner.new()
        val res = spawner.spawnObj(FutureObj.new(ready(Unit)))
        assertTrue(res.isSuccess)

        val localRes = spawner.spawnLocalObj(LocalFutureObj.new(ready(Unit)))
        assertTrue(localRes.isSuccess)

        val mutSpawner = noopSpawnerMut()
        assertTrue(mutSpawner.status().isSuccess)
    }

    @Test
    fun testPanicSpawner() {
        val spawner = PanicSpawner.new()
        assertFailsWith<IllegalStateException> {
            spawner.spawnObj(FutureObj.new(ready(Unit)))
        }

        assertFailsWith<IllegalStateException> {
            spawner.spawnLocalObj(LocalFutureObj.new(ready(Unit)))
        }

        val mutSpawner = panicSpawnerMut()
        assertFailsWith<IllegalStateException> {
            mutSpawner.spawnObj(FutureObj.new(ready(Unit)))
        }
    }

    @Test
    fun testRecordSpawner() {
        val spawner = RecordSpawner.new()
        assertEquals(0, spawner.spawned.size)

        val fut1 = FutureObj.new(ready(Unit))
        spawner.spawnObj(fut1)
        assertEquals(1, spawner.spawned.size)

        val localFut = LocalFutureObj.new(ready(Unit))
        spawner.spawnLocalObj(localFut)
        assertEquals(2, spawner.spawned.size)
    }

    @Test
    fun testStreamAssertions() {
        val pendingFut = ready(42).pendingOnce()
        val s = streamOnce(pendingFut)

        assertStreamPending(s)
        assertStreamNext(s, 42)
        assertStreamDone(s)
    }
}
