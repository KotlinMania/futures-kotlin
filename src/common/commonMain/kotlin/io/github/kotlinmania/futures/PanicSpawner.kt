// port-lint: source futures-test/src/task/panic_spawner.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * An implementation of [Spawn] and [LocalSpawn] that throws an error when used.
 */
@HiddenFromObjC
public class PanicSpawner :
    Spawn,
    LocalSpawn {
    override fun spawnObj(future: FutureObj<Unit>): Result<Unit> =
        error("should not spawn")

    override fun spawnLocalObj(future: LocalFutureObj<Unit>): Result<Unit> =
        error("should not spawn")

    public companion object {
        private val INSTANCE: PanicSpawner = PanicSpawner()

        /**
         * Create a new instance of [PanicSpawner].
         */
        public fun new(): PanicSpawner = PanicSpawner()

        /**
         * Shared singleton instance of [PanicSpawner].
         */
        public fun instance(): PanicSpawner = INSTANCE
    }
}

/**
 * Get a reference to a singleton instance of [PanicSpawner].
 */
@HiddenFromObjC
public fun panicSpawnerMut(): PanicSpawner = PanicSpawner.instance()
