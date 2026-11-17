@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Result of polling an asynchronous computation.
 */
public sealed class Poll<out T> {
    /**
     * The computation produced a value.
     */
    public data class Ready<out T>(
        public val value: T,
    ) : Poll<T>()

    /**
     * The computation is not ready yet.
     */
    public data object Pending : Poll<Nothing>()

    /**
     * Returns `true` if this poll result is [Poll.Ready].
     */
    public fun isReady(): Boolean = this is Ready

    /**
     * Returns `true` if this poll result is [Poll.Pending].
     */
    public fun isPending(): Boolean = this is Pending

    public companion object {
        public fun <T> ready(value: T): Poll<T> = Ready(value)

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
    public val waker: Waker,
) {
    public constructor() : this(Waker {})

    public fun wakeByRef() {
        waker.wakeByRef()
    }

    public companion object {
        public fun fromWaker(waker: Waker): TaskContext = TaskContext(waker)
    }
}

@HiddenFromObjC
public inline fun <T, R> Poll<T>.fold(onReady: (T) -> R, onPending: () -> R): R =
    when (this) {
        is Poll.Ready -> onReady(value)
        Poll.Pending -> onPending()
    }

public fun <T> Poll<T>.readyOrNull(): T? =
    when (this) {
        is Poll.Ready -> value
        Poll.Pending -> null
    }
