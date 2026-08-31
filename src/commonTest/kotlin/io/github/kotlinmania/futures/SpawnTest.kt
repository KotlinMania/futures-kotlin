// port-lint: tests futures-test/src/task/noop_spawner.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@HiddenFromObjC
class RecordSpawner : Spawn, LocalSpawn {
    val spawned = mutableListOf<Future<Unit>>()

    override fun spawnObj(future: FutureObj<Unit>): Result<Unit> {
        spawned.add(future)
        return Result.success(Unit)
    }

    override fun spawnLocalObj(future: LocalFutureObj<Unit>): Result<Unit> {
        spawned.add(future)
        return Result.success(Unit)
    }
}

class SpawnTest {
    @Test
    fun spawnErrorShutdown() {
        val err = SpawnError.shutdown()
        assertTrue(err.isShutdown())
        assertEquals("SpawnError(shutdown)", err.toString())
    }

    @Test
    fun spawnerRecordsFutures() {
        val spawner = RecordSpawner()
        assertEquals(Result.success(Unit), spawner.status())
        assertEquals(Result.success(Unit), spawner.statusLocal())

        val res = spawner.spawn(Future { Poll.Ready(Unit) })
        assertTrue(res.isSuccess)
        assertEquals(1, spawner.spawned.size)

        val localRes = spawner.spawnLocal(Future { Poll.Ready(Unit) })
        assertTrue(localRes.isSuccess)
        assertEquals(2, spawner.spawned.size)
    }

    @Test
    fun spawnWithHandle() {
        val spawner = RecordSpawner()
        val res = spawner.spawnWithHandle(Future { Poll.Ready(100) })
        assertTrue(res.isSuccess)
        assertEquals(1, spawner.spawned.size)

        // Poll the spawned remote future
        val cx = TaskContext()
        val remote = spawner.spawned[0]
        val p = remote.poll(cx)
        assertTrue(p is Poll.Ready)

        // Now poll the handle
        val handle = res.getOrThrow()
        val hp = handle.poll(cx)
        assertTrue(hp is Poll.Ready)
        assertEquals(100, hp.value)
    }
}
