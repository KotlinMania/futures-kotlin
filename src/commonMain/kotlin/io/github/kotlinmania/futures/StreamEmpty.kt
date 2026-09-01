// port-lint: source stream/empty.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [emptyStream] function.
 */
@HiddenFromObjC
public class EmptyStream<out T> : FusedStream<T> {
    override fun isTerminated(): Boolean = true

    override fun pollNext(context: TaskContext): Poll<Yield<T>> =
        Poll.ready(Yield.end())

    override fun sizeHint(): SizeHint = SizeHint(0, 0)
}

/**
 * Creates a stream which contains no elements.
 *
 * The returned stream will always return `Poll.Ready(Yield.End)` when polled.
 */
@HiddenFromObjC
public fun <T> emptyStream(): EmptyStream<T> = EmptyStream()
