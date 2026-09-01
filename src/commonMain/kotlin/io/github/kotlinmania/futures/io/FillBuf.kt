// port-lint: source io/fill_buf.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Future for the [AsyncBufRead.fillBuf] method.
 */
@HiddenFromObjC
public class FillBuf(
    private val reader: AsyncBufRead,
) : Future<Try<ByteArray, IoError>> {
    public interface Output

    public companion object {
        public fun new(reader: AsyncBufRead): FillBuf = FillBuf(reader)
    }

    override fun poll(context: TaskContext): Poll<Try<ByteArray, IoError>> = reader.pollFillBuf(context)
}

