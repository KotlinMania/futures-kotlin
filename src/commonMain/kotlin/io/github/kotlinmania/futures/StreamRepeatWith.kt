// port-lint: source stream/repeat_with.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * A stream that repeats elements endlessly by applying the provided closure.
 */
@HiddenFromObjC
public class RepeatWith<T>(
    private val repeater: () -> T,
) : FusedStream<T> {
    override fun isTerminated(): Boolean = false

    override fun pollNext(context: TaskContext): Poll<Yield<T>> =
        Poll.ready(Yield.value(repeater()))

    override fun sizeHint(): SizeHint = SizeHint(Int.MAX_VALUE, null)
}

/**
 * Creates a new stream that repeats elements endlessly by applying the provided closure.
 */
@HiddenFromObjC
public fun <T> streamRepeatWith(repeater: () -> T): RepeatWith<T> = RepeatWith(repeater)
