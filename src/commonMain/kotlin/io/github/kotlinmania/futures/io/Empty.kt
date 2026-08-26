// port-lint: source futures-util/src/io/empty.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Reader for the [empty] function.
 */
@HiddenFromObjC
public class Empty : AsyncBufRead {
    override fun pollRead(
        context: TaskContext,
        buf: ByteArray,
        offset: Int,
        length: Int,
    ): Poll<Try<Int, IoError>> = Poll.Ready(Try.ok(0))

    override fun pollFillBuf(context: TaskContext): Poll<Try<ByteArray, IoError>> =
        Poll.Ready(Try.ok(ByteArray(0)))

    override fun consume(amt: Int) {}

    override fun toString(): String = "Empty"
}

/**
 * Constructs a new handle to an empty reader.
 *
 * All reads from the returned reader will return `Poll.Ready(Try.ok(0))`.
 */
@HiddenFromObjC
public fun empty(): Empty = Empty()
