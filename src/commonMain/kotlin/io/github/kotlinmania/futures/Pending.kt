// port-lint: source futures-util/src/future/pending.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [pending] function.
 */
@HiddenFromObjC
public class Pending<T> : FusedFuture<T> {
    override fun isTerminated(): Boolean = true

    override fun poll(context: TaskContext): Poll<T> = Poll.Pending
}

/**
 * Creates a future which never resolves, representing a computation that never finishes.
 */
@HiddenFromObjC
public fun <T> pending(): Pending<T> = Pending()
