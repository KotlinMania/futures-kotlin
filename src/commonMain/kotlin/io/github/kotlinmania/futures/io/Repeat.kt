// port-lint: source futures-util/src/io/repeat.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Reader for the [repeat] function.
 */
@HiddenFromObjC
public class Repeat(
    private val byte: Byte,
) : AsyncRead {
    override fun pollRead(
        context: TaskContext,
        buf: ByteArray,
        offset: Int,
        length: Int,
    ): Poll<Try<Int, IoError>> {
        val count = minOf(length, buf.size - offset)
        for (i in offset until (offset + count)) {
            buf[i] = byte
        }
        return Poll.Ready(Try.ok(count))
    }

    override fun toString(): String = "Repeat(byte=$byte)"
}

/**
 * Creates an instance of a reader that infinitely repeats one byte.
 */
@HiddenFromObjC
public fun repeat(byte: Byte): Repeat = Repeat(byte)
