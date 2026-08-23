// port-lint: source futures-util/src/future/poll_fn.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [pollFn] function.
 */
@HiddenFromObjC
public class PollFn<T>(
    private val block: (TaskContext) -> Poll<T>,
) : Future<T> {
    override fun poll(context: TaskContext): Poll<T> = block(context)
}

/**
 * Creates a new future wrapping around a function returning [Poll].
 */
@HiddenFromObjC
public fun <T> pollFn(block: (TaskContext) -> Poll<T>): PollFn<T> = PollFn(block)
