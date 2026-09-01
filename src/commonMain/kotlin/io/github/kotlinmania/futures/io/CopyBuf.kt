// port-lint: source io/copy_buf.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Creates a future which copies all the bytes from one object to another using buffered reading.
 */
@HiddenFromObjC
public class CopyBuf(
    private val reader: AsyncBufRead,
    private val writer: AsyncWrite,
) : Future<Try<Long, IoError>> {
    private var amt: Long = 0L

    override fun poll(context: TaskContext): Poll<Try<Long, IoError>> {
        while (true) {
            val fillRes = reader.pollFillBuf(context)
            when (fillRes) {
                is Poll.Pending -> return Poll.Pending
                is Poll.Ready -> {
                    when (val result = fillRes.value) {
                        is Try.Err -> return Poll.Ready(Try.err(result.error))
                        is Try.Ok -> {
                            val buffer = result.value
                            if (buffer.isEmpty()) {
                                val flushRes = writer.pollFlush(context)
                                when (flushRes) {
                                    is Poll.Pending -> return Poll.Pending
                                    is Poll.Ready -> {
                                        when (val flushResult = flushRes.value) {
                                            is Try.Err -> return Poll.Ready(Try.err(flushResult.error))
                                            is Try.Ok -> return Poll.Ready(Try.ok(amt))
                                        }
                                    }
                                }
                            } else {
                                val writeRes = writer.pollWrite(context, buffer, 0, buffer.size)
                                when (writeRes) {
                                    is Poll.Pending -> return Poll.Pending
                                    is Poll.Ready -> {
                                        when (val writeResult = writeRes.value) {
                                            is Try.Err -> return Poll.Ready(Try.err(writeResult.error))
                                            is Try.Ok -> {
                                                val n = writeResult.value
                                                if (n == 0) {
                                                    return Poll.Ready(Try.err(IoError(IoErrorKind.WriteZero, "zero bytes written")))
                                                }
                                                amt += n.toLong()
                                                reader.consume(n)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
