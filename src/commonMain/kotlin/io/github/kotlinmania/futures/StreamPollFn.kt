// port-lint: source stream/poll_fn.rs
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
    public interface Item

    override fun pollNext(context: TaskContext): Poll<Yield<T>> = f(context)

    public fun fmt(): String = "PollFn"

    override fun toString(): String = fmt()

    public companion object {
        public fun <T> new(f: (TaskContext) -> Poll<Yield<T>>): StreamPollFn<T> = StreamPollFn(f)
    }
}


/**
 * Creates a new stream wrapping a function returning `Poll<Yield<T>>`.
 */
@HiddenFromObjC
public fun <T> streamPollFn(f: (TaskContext) -> Poll<Yield<T>>): StreamPollFn<T> =
    StreamPollFn(f)

