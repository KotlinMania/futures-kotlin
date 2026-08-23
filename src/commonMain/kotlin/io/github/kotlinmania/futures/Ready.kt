// port-lint: source futures-util/src/future/ready.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [ready] function.
 */
@HiddenFromObjC
public class Ready<T>(
    private var value: T?,
    private var hasValue: Boolean = true,
) : FusedFuture<T> {
    public constructor(value: T) : this(value, true)

    /**
     * Unwraps the value from this immediately ready future.
     */
    public fun intoInner(): T {
        check(hasValue) { "Ready value already taken" }
        hasValue = false
        val v = value
        value = null
        @Suppress("UNCHECKED_CAST")
        return v as T
    }

    override fun isTerminated(): Boolean = !hasValue

    override fun poll(context: TaskContext): Poll<T> {
        check(hasValue) { "Ready polled after completion" }
        hasValue = false
        val v = value
        value = null
        @Suppress("UNCHECKED_CAST")
        return Poll.Ready(v as T)
    }
}

/**
 * Creates a future that is immediately ready with a value.
 */
@HiddenFromObjC
public fun <T> ready(value: T): Ready<T> = Ready(value)

/**
 * Create a future that is immediately ready with a success value.
 */
@HiddenFromObjC
public fun <T, E> ok(value: T): Ready<Try<T, E>> = Ready(Try.Ok(value))

/**
 * Create a future that is immediately ready with an error value.
 */
@HiddenFromObjC
public fun <T, E> err(error: E): Ready<Try<T, E>> = Ready(Try.Err(error))
