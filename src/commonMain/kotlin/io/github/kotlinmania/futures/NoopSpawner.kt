// port-lint: source futures-test/src/task/noop_spawner.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * An implementation of [Spawn] and [LocalSpawn] that discards spawned futures when used.
 */
@HiddenFromObjC
public class NoopSpawner :
    Spawn,
    LocalSpawn {
    override fun spawnObj(future: FutureObj<Unit>): Result<Unit> = Result.success(Unit)

    override fun spawnLocalObj(future: LocalFutureObj<Unit>): Result<Unit> = Result.success(Unit)

    public companion object {
        private val INSTANCE: NoopSpawner = NoopSpawner()

        /**
         * Create a new instance of [NoopSpawner].
         */
        public fun new(): NoopSpawner = NoopSpawner()

        /**
         * Shared singleton instance of [NoopSpawner].
         */
        public fun instance(): NoopSpawner = INSTANCE
    }
}

/**
 * Get a reference to a singleton instance of [NoopSpawner].
 */
@HiddenFromObjC
public fun noopSpawnerMut(): NoopSpawner = NoopSpawner.instance()
