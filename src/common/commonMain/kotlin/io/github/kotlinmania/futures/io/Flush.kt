// port-lint: source io/flush.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Future for the [AsyncWrite.flush] method.
 */
@HiddenFromObjC
public class Flush(
    private val writer: AsyncWrite,
) : Future<Try<Unit, IoError>> {
    public interface Output

    public companion object {
        public fun new(writer: AsyncWrite): Flush = Flush(writer)
    }

    override fun poll(context: TaskContext): Poll<Try<Unit, IoError>> =
        writer.pollFlush(context)
}
