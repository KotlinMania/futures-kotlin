// port-lint: source io/write.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Future for the [AsyncWrite.write] method.
 */
@HiddenFromObjC
public class Write(
    private val writer: AsyncWrite,
    private val buf: ByteArray,
    private val offset: Int = 0,
    private val length: Int = buf.size - offset,
) : Future<Try<Int, IoError>> {
    public interface Output

    public companion object {
        public fun new(
            writer: AsyncWrite,
            buf: ByteArray,
            offset: Int = 0,
            length: Int = buf.size - offset,
        ): Write = Write(writer, buf, offset, length)
    }

    override fun poll(context: TaskContext): Poll<Try<Int, IoError>> =
        writer.pollWrite(context, buf, offset, length)
}

