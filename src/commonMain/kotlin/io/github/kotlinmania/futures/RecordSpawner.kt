// port-lint: source futures-test/src/task/record_spawner.rs
@file:OptIn(
    kotlin.experimental.ExperimentalObjCRefinement::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * An implementation of [Spawn] and [LocalSpawn] that records any futures spawned on it.
 */
@HiddenFromObjC
public class RecordSpawner :
    Spawn,
    LocalSpawn {
    private val spawnedFutures = mutableListOf<FutureObj<Unit>>()
    private val lock = Lock.new(Unit)

    /**
     * Inspect any futures that were spawned onto this [Spawn].
     */
    public val spawned: List<FutureObj<Unit>>
        get() = spawned()

    /**
     * Inspect any futures that were spawned onto this [Spawn].
     */
    public fun spawned(): List<FutureObj<Unit>> {
        var slot = lock.tryLock()
        while (slot == null) {
            slot = lock.tryLock()
        }
        try {
            return spawnedFutures.toList()
        } finally {
            slot.unlock()
        }
    }

    override fun spawnObj(future: FutureObj<Unit>): Result<Unit> {
        var slot = lock.tryLock()
        while (slot == null) {
            slot = lock.tryLock()
        }
        try {
            spawnedFutures.add(future)
            return Result.success(Unit)
        } finally {
            slot.unlock()
        }
    }

    override fun spawnLocalObj(future: LocalFutureObj<Unit>): Result<Unit> {
        var slot = lock.tryLock()
        while (slot == null) {
            slot = lock.tryLock()
        }
        try {
            spawnedFutures.add(future.intoFutureObj())
            return Result.success(Unit)
        } finally {
            slot.unlock()
        }
    }

    public companion object {
        /**
         * Create a new instance of [RecordSpawner].
         */
        public fun new(): RecordSpawner = RecordSpawner()
    }
}
