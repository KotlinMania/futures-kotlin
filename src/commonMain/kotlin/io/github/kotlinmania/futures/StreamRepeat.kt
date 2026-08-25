// port-lint: source futures-util/src/stream/repeat.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [streamRepeat] function.
 */
@HiddenFromObjC
public class Repeat<T>(
    private val item: T,
) : FusedStream<T> {
    override fun isTerminated(): Boolean = false

    override fun pollNext(context: TaskContext): Poll<Yield<T>> =
        Poll.ready(Yield.value(item))

    override fun sizeHint(): SizeHint = SizeHint(Int.MAX_VALUE, null)
}

/**
 * Create a stream which produces the same item repeatedly.
 *
 * The stream never terminates.
 */
@HiddenFromObjC
public fun <T> streamRepeat(item: T): Repeat<T> = Repeat(item)
