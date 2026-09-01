// port-lint: source io/copy.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Creates a future which copies all the bytes from one object to another.
 */
@HiddenFromObjC
public fun copy(reader: AsyncRead, writer: AsyncWrite): Copy =
    Copy(if (reader is AsyncBufRead) reader else BufReader(reader), writer)

/**
 * Future for the [copy] function.
 */
@HiddenFromObjC
public class Copy(
    reader: AsyncBufRead,
    writer: AsyncWrite,
) : Future<Try<Long, IoError>> {
    private val copyBuf = CopyBuf(reader, writer)

    override fun poll(context: TaskContext): Poll<Try<Long, IoError>> =
        copyBuf.poll(context)
}
