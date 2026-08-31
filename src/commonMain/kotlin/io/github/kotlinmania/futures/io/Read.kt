// port-lint: source futures-util/src/io/read.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Future for the [AsyncRead.read] method.
 */
@HiddenFromObjC
public class Read(
    private val reader: AsyncRead,
    private val buf: ByteArray,
    private val offset: Int = 0,
    private val length: Int = buf.size - offset,
) : Future<Try<Int, IoError>> {
    public interface Output

    public companion object {
        public fun new(
            reader: AsyncRead,
            buf: ByteArray,
            offset: Int = 0,
            length: Int = buf.size - offset,
        ): Read = Read(reader, buf, offset, length)
    }

    override fun poll(context: TaskContext): Poll<Try<Int, IoError>> =
        reader.pollRead(context, buf, offset, length)
}

