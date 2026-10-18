// port-lint: source io/write_vectored.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Future for the [AsyncWriteExt.writeVectored] method.
 */
@HiddenFromObjC
public class WriteVectored(
    private val writer: AsyncWrite,
    private val bufs: List<IoSlice>,
) : Future<Try<Int, IoError>> {
    public interface Output

    public companion object {
        public fun new(writer: AsyncWrite, bufs: List<IoSlice>): WriteVectored =
            WriteVectored(writer, bufs)
    }

    override fun poll(context: TaskContext): Poll<Try<Int, IoError>> =
        writer.pollWriteVectored(context, bufs)
}
