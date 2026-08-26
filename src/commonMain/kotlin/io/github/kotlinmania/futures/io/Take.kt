// port-lint: source futures-util/src/io/take.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Reader for the [AsyncRead.take] method.
 */
@HiddenFromObjC
public class Take<R : AsyncRead>(
    public val inner: R,
    private var limit: Long,
) : AsyncRead {
    /**
     * Returns the remaining number of bytes that can be read before this instance will return EOF.
     */
    public fun limit(): Long = limit

    /**
     * Sets the number of bytes that can be read before this instance will return EOF.
     */
    public fun setLimit(limit: Long) {
        this.limit = limit
    }

    override fun pollRead(
        context: TaskContext,
        buf: ByteArray,
        offset: Int,
        length: Int,
    ): Poll<Try<Int, IoError>> {
        if (limit <= 0) {
            return Poll.Ready(Try.ok(0))
        }
        val maxToRead = minOf(length.toLong(), limit).toInt()
        val res = inner.pollRead(context, buf, offset, maxToRead)
        return when (res) {
            is Poll.Pending -> Poll.Pending
            is Poll.Ready ->
                when (val r = res.value) {
                    is Try.Err -> Poll.Ready(Try.err(r.error))
                    is Try.Ok -> {
                        val n = r.value
                        limit -= n
                        Poll.Ready(Try.ok(n))
                    }
                }
        }
    }
}
