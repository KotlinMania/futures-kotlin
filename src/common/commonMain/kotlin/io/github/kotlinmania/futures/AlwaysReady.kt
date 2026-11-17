// port-lint: source future/always_ready.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [alwaysReady] function.
 */
@HiddenFromObjC
public class AlwaysReady<T>(
    private val producer: () -> T,
) : FusedFuture<T> {
    override fun isTerminated(): Boolean = false

    override fun poll(context: TaskContext): Poll<T> = Poll.Ready(producer())
}

/**
 * Creates a future that is always immediately ready with a value.
 */
@HiddenFromObjC
public fun <T> alwaysReady(producer: () -> T): AlwaysReady<T> = AlwaysReady(producer)
