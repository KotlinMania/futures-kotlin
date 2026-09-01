// port-lint: source stream/pending.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [pendingStream] function.
 */
@HiddenFromObjC
public class PendingStream<out T> : FusedStream<T> {
    override fun isTerminated(): Boolean = false

    override fun pollNext(context: TaskContext): Poll<Yield<T>> =
        Poll.pending()

    override fun sizeHint(): SizeHint = SizeHint(0, null)
}

/**
 * Creates a stream which never returns any elements.
 *
 * The returned stream will always return [Poll.Pending] when polled.
 */
@HiddenFromObjC
public fun <T> pendingStream(): PendingStream<T> = PendingStream()
