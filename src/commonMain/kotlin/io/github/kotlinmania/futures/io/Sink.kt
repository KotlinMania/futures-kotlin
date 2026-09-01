// port-lint: source io/sink.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Writer for the [sink] function.
 */
@HiddenFromObjC
public class Sink : AsyncWrite {
    override fun pollWrite(
        context: TaskContext,
        buf: ByteArray,
        offset: Int,
        length: Int,
    ): Poll<Try<Int, IoError>> {
        require(offset >= 0 && length >= 0 && offset + length <= buf.size) {
            "Offset and length out of bounds"
        }
        return Poll.Ready(Try.ok(length))
    }

    public fun pollWriteVectored(
        context: TaskContext,
        bufs: List<ByteArray>,
    ): Poll<Try<Int, IoError>> = Poll.Ready(Try.ok(bufs.sumOf { it.size }))

    override fun pollFlush(context: TaskContext): Poll<Try<Unit, IoError>> =
        Poll.Ready(Try.ok(Unit))

    override fun pollClose(context: TaskContext): Poll<Try<Unit, IoError>> =
        Poll.Ready(Try.ok(Unit))

    override fun toString(): String = "Sink { .. }"
}

/**
 * Creates an instance of a writer which will successfully consume all data.
 *
 * All calls to `pollWrite` on the returned instance will return `Poll.Ready(Try.ok(length))`
 * and the contents of the buffer will not be inspected.
 */
@HiddenFromObjC
public fun sink(): Sink = Sink()
