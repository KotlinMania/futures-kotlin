// port-lint: source io/read_vectored.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Future for the [AsyncReadExt.readVectored] method.
 */
@HiddenFromObjC
public class ReadVectored(
    private val reader: AsyncRead,
    private val bufs: List<IoSliceMut>,
) : Future<Try<Int, IoError>> {
    public interface Output

    public companion object {
        public fun new(reader: AsyncRead, bufs: List<IoSliceMut>): ReadVectored =
            ReadVectored(reader, bufs)
    }

    override fun poll(context: TaskContext): Poll<Try<Int, IoError>> =
        reader.pollReadVectored(context, bufs)
}
