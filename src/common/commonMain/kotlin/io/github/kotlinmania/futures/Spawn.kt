// port-lint: source task/spawn.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * An error that occurred during spawning.
 */
public class SpawnError private constructor(
    private val isShutdownError: Boolean = true,
) {
    /**
     * Check whether spawning failed due to the executor being shut down.
     */
    public fun isShutdown(): Boolean = isShutdownError

    override fun toString(): String = "SpawnError(shutdown)"

    public companion object {
        /**
         * Spawning failed because the executor has been shut down.
         */
        public fun shutdown(): SpawnError = SpawnError(true)
    }
}

/**
 * The [Spawn] interface allows for pushing futures onto an executor that will
 * run them to completion.
 */
@HiddenFromObjC
public interface Spawn {
    /**
     * Spawns a future that will be run to completion.
     */
    public fun spawnObj(future: FutureObj<Unit>): Result<Unit>

    /**
     * Determines whether the executor is able to spawn new tasks.
     */
    public fun status(): Result<Unit> = Result.success(Unit)
}

/**
 * The [LocalSpawn] interface is similar to [Spawn], but allows spawning futures
 * without thread-safety requirements.
 */
@HiddenFromObjC
public interface LocalSpawn {
    /**
     * Spawns a future that will be run to completion.
     */
    public fun spawnLocalObj(future: LocalFutureObj<Unit>): Result<Unit>

    /**
     * Determines whether the executor is able to spawn new tasks.
     */
    public fun statusLocal(): Result<Unit> = Result.success(Unit)
}

/**
 * Spawns a task that polls the given future with output [Unit] to completion.
 */
@HiddenFromObjC
public fun Spawn.spawn(future: Future<Unit>): Result<Unit> =
    spawnObj(FutureObj.new(future))

/**
 * Spawns a task that polls the given future to completion and returns a
 * future that resolves to the spawned future's output.
 */
@HiddenFromObjC
public fun <T> Spawn.spawnWithHandle(future: Future<T>): Result<RemoteHandle<T>> {
    val (remote, handle) = future.remoteHandle()
    return spawn(remote).map { handle }
}

/**
 * Spawns a task that polls the given future with output [Unit] to completion.
 */
@HiddenFromObjC
public fun LocalSpawn.spawnLocal(future: Future<Unit>): Result<Unit> =
    spawnLocalObj(LocalFutureObj.new(future))

/**
 * Spawns a local task that polls the given future to completion and returns a
 * future that resolves to the spawned future's output.
 */
@HiddenFromObjC
public fun <T> LocalSpawn.spawnLocalWithHandle(future: Future<T>): Result<RemoteHandle<T>> {
    val (remote, handle) = future.remoteHandle()
    return spawnLocal(remote).map { handle }
}
