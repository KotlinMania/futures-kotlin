// port-lint: source futures-util/src/stream/poll_fn.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [streamPollFn] function.
 */
@HiddenFromObjC
public class StreamPollFn<T>(
    private val f: (TaskContext) -> Poll<Yield<T>>,
) : Stream<T> {
    override fun pollNext(context: TaskContext): Poll<Yield<T>> = f(context)
}

/**
 * Creates a new stream wrapping a function returning `Poll<Yield<T>>`.
 */
@HiddenFromObjC
public fun <T> streamPollFn(f: (TaskContext) -> Poll<Yield<T>>): StreamPollFn<T> =
    StreamPollFn(f)
