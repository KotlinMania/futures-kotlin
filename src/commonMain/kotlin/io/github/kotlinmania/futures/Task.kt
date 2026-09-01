// port-lint: source task/mod.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Result of polling an asynchronous computation.
 *
 * Hidden from Swift Export: the generic sealed family otherwise emits
 * `Any?` → `T` unchecked-cast bridges in `KotlinStdlib.kt` that fail
 * compilation under `allWarningsAsErrors`. See `SWIFT_EXPORT_ROLLOUT.md`
 * gap #3 (first-choice fix) and gap #4.
 */
@HiddenFromObjC
public sealed interface Poll<out T> {
    /**
     * The computation produced a value.
     */
    @HiddenFromObjC
    public data class Ready<out T>(
        public val value: T,
    ) : Poll<T>

    /**
     * The computation is not ready yet.
     */
    @HiddenFromObjC
    public data object Pending : Poll<Nothing>

    public companion object {
        @HiddenFromObjC
        public fun <T> ready(value: T): Poll<T> = Ready(value)

        @HiddenFromObjC
        public fun <T> pending(): Poll<T> = Pending
    }
}

/**
 * Notification handle for a task that may be polled again.
 */
public fun interface Waker {
    public fun wakeByRef()
}

public fun Waker.wake() {
    wakeByRef()
}

/**
 * Polling context passed to futures, streams, and sinks.
 */
public class TaskContext(
    public val waker: Waker = Waker {},
) {
    public fun wakeByRef() {
        waker.wakeByRef()
    }
}

@HiddenFromObjC
public inline fun <T, R> Poll<T>.fold(onReady: (T) -> R, onPending: () -> R): R =
    when (this) {
        is Poll.Ready -> onReady(value)
        Poll.Pending -> onPending()
    }

@HiddenFromObjC
public fun <T> Poll<T>.readyOrNull(): T? =
    when (this) {
        is Poll.Ready -> value
        Poll.Pending -> null
    }
