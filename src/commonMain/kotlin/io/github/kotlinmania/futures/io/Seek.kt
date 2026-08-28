// port-lint: source futures-util/src/io/seek.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Future for the [seek] method.
 */
@HiddenFromObjC
public class Seek<out S : AsyncSeek>(
    public val seek: S,
    public val pos: SeekFrom,
) : Future<Try<Long, IoError>> {
    override fun poll(context: TaskContext): Poll<Try<Long, IoError>> =
        seek.pollSeek(context, pos)
}
