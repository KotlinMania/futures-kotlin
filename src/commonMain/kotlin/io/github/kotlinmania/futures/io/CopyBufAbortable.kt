// port-lint: source futures-util/src/io/copy_buf_abortable.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.AbortHandle
import io.github.kotlinmania.futures.AbortInner
import io.github.kotlinmania.futures.Aborted
import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Future for the [copyBufAbortable] function.
 */
@HiddenFromObjC
public class CopyBufAbortable internal constructor(
    private val reader: AsyncBufRead,
    private val writer: AsyncWrite,
    internal val inner: AbortInner,
) : Future<Try<Try<Long, Aborted>, IoError>> {
    private var amt: Long = 0L

    override fun poll(context: TaskContext): Poll<Try<Try<Long, Aborted>, IoError>> {
        while (true) {
            if (inner.aborted.load()) {
                return Poll.Ready(Try.ok(Try.err(Aborted)))
            }

            val fillRes = reader.pollFillBuf(context)
            when (fillRes) {
                is Poll.Pending -> break
                is Poll.Ready -> {
                    when (val result = fillRes.value) {
                        is Try.Err -> return Poll.Ready(Try.err(result.error))
                        is Try.Ok -> {
                            val buffer = result.value
                            if (buffer.isEmpty()) {
                                val flushRes = writer.pollFlush(context)
                                when (flushRes) {
                                    is Poll.Pending -> break
                                    is Poll.Ready -> {
                                        when (val flushResult = flushRes.value) {
                                            is Try.Err -> return Poll.Ready(Try.err(flushResult.error))
                                            is Try.Ok -> return Poll.Ready(Try.ok(Try.ok(amt)))
                                        }
                                    }
                                }
                            } else {
                                val writeRes = writer.pollWrite(context, buffer, 0, buffer.size)
                                when (writeRes) {
                                    is Poll.Pending -> break
                                    is Poll.Ready -> {
                                        when (val writeResult = writeRes.value) {
                                            is Try.Err -> return Poll.Ready(Try.err(writeResult.error))
                                            is Try.Ok -> {
                                                val n = writeResult.value
                                                if (n == 0) {
                                                    return Poll.Ready(
                                                        Try.err(
                                                            IoError(
                                                                IoErrorKind.WriteZero,
                                                                "zero bytes written",
                                                            ),
                                                        ),
                                                    )
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

        inner.waker.register(context.waker)
        if (inner.aborted.load()) {
            return Poll.Ready(Try.ok(Try.err(Aborted)))
        }

        return Poll.Pending
    }
}

/**
 * Creates a future which copies all the bytes from [reader] to [writer], along with its [AbortHandle].
 */
@HiddenFromObjC
public fun copyBufAbortable(
    reader: AsyncBufRead,
    writer: AsyncWrite,
): Pair<CopyBufAbortable, AbortHandle> {
    val (handle, reg) = AbortHandle.newPair()
    return Pair(CopyBufAbortable(reader, writer, reg.inner), handle)
}
