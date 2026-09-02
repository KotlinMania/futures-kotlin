// port-lint: source async_await/poll.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Extracts the successful value of a [Poll.Ready], or returns early with null if [Poll.Pending].
 *
 * Mirrors the upstream ready macro from task poll.
 */
@HiddenFromObjC
public fun <T> ready(poll: Poll<T>): T? =
    when (poll) {
        is Poll.Ready -> poll.value
        Poll.Pending -> null
    }

/**
 * Returns true if this poll result is [Poll.Ready].
 */
@HiddenFromObjC
public fun <T> Poll<T>.isReady(): Boolean = this is Poll.Ready

/**
 * Returns true if this poll result is [Poll.Pending].
 */
@HiddenFromObjC
public fun <T> Poll<T>.isPending(): Boolean = this is Poll.Pending

/**
 * Unwraps the value of [Poll.Ready], throwing an exception if [Poll.Pending].
 */
@HiddenFromObjC
public fun <T> Poll<T>.unwrap(): T =
    when (this) {
        is Poll.Ready -> value
        Poll.Pending -> throw IllegalStateException("called Poll.unwrap() on a Pending value")
    }

