// port-lint: source futures-util/src/io/write_all.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Future for the [AsyncWrite.writeAll] method.
 */
@HiddenFromObjC
public class WriteAll(
    private val writer: AsyncWrite,
    private val buf: ByteArray,
    private var offset: Int = 0,
    private var length: Int = buf.size - offset,
) : Future<Try<Unit, IoError>> {
    override fun poll(context: TaskContext): Poll<Try<Unit, IoError>> {
        while (length > 0) {
            val pollRes = writer.pollWrite(context, buf, offset, length)
            when (pollRes) {
                is Poll.Pending -> return Poll.Pending
                is Poll.Ready -> {
                    when (val result = pollRes.value) {
                        is Try.Err -> return Poll.Ready(Try.err(result.error))
                        is Try.Ok -> {
                            val n = result.value
                            if (n == 0) {
                                return Poll.Ready(Try.err(IoError(IoErrorKind.WriteZero, "failed to write whole buffer")))
                            }
                            offset += n
                            length -= n
                        }
                    }
                }
            }
        }
        return Poll.Ready(Try.ok(Unit))
    }
}
