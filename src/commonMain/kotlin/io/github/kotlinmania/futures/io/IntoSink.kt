// port-lint: source io/into_sink.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.Sink
import io.github.kotlinmania.futures.SinkOutcome
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

private class Block(
    val bytes: ByteArray,
    var offset: Int = 0,
)

/**
 * Sink for the [intoSink] method.
 */
@HiddenFromObjC
public class IntoSink<out W : AsyncWrite>(
    public val writer: W,
) : Sink<ByteArray, IoError> {
    public interface Error

    public companion object {
        public fun <W : AsyncWrite> new(writer: W): IntoSink<W> = IntoSink(writer)
    }

    public fun fmt(): String = "IntoSink"

    override fun toString(): String = fmt()

    private var buffer: Block? = null

    private fun pollFlushBuffer(context: TaskContext): Poll<Try<Unit, IoError>> {
        val block = buffer ?: return Poll.Ready(Try.ok(Unit))
        while (block.offset < block.bytes.size) {
            val pollRes =
                writer.pollWrite(
                    context,
                    block.bytes,
                    block.offset,
                    block.bytes.size - block.offset,
                )
            when (pollRes) {
                is Poll.Pending -> return Poll.Pending
                is Poll.Ready -> {
                    when (val result = pollRes.value) {
                        is Try.Err -> return Poll.Ready(Try.err(result.error))
                        is Try.Ok -> {
                            val n = result.value
                            if (n == 0) {
                                return Poll.Ready(
                                    Try.err(
                                        IoError(
                                            IoErrorKind.WriteZero,
                                            "failed to write all bytes to writer",
                                        ),
                                    ),
                                )
                            }
                            block.offset += n
                        }
                    }
                }
            }
        }
        buffer = null
        return Poll.Ready(Try.ok(Unit))
    }

    override fun pollReady(context: TaskContext): Poll<SinkOutcome<IoError>> {
        val flushRes = pollFlushBuffer(context)
        return when (flushRes) {
            is Poll.Pending -> Poll.Pending
            is Poll.Ready -> {
                when (val r = flushRes.value) {
                    is Try.Err -> Poll.Ready(SinkOutcome.err(r.error))
                    is Try.Ok -> Poll.Ready(SinkOutcome.ready())
                }
            }
        }
    }

    override fun startSend(item: ByteArray): SinkOutcome<IoError> {
        buffer = Block(item, offset = 0)
        return SinkOutcome.ready()
    }

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<IoError>> {
        val flushBufferRes = pollFlushBuffer(context)
        when (flushBufferRes) {
            is Poll.Pending -> return Poll.Pending
            is Poll.Ready -> {
                val resVal = flushBufferRes.value
                if (resVal is Try.Err) {
                    return Poll.Ready(SinkOutcome.err(resVal.error))
                }
            }
        }
        val writerFlushRes = writer.pollFlush(context)
        return when (writerFlushRes) {
            is Poll.Pending -> Poll.Pending
            is Poll.Ready -> {
                when (val r = writerFlushRes.value) {
                    is Try.Err -> Poll.Ready(SinkOutcome.err(r.error))
                    is Try.Ok -> Poll.Ready(SinkOutcome.ready())
                }
            }
        }
    }

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<IoError>> {
        val flushBufferRes = pollFlushBuffer(context)
        when (flushBufferRes) {
            is Poll.Pending -> return Poll.Pending
            is Poll.Ready -> {
                val resVal = flushBufferRes.value
                if (resVal is Try.Err) {
                    return Poll.Ready(SinkOutcome.err(resVal.error))
                }
            }
        }
        val writerCloseRes = writer.pollClose(context)
        return when (writerCloseRes) {
            is Poll.Pending -> Poll.Pending
            is Poll.Ready -> {
                when (val r = writerCloseRes.value) {
                    is Try.Err -> Poll.Ready(SinkOutcome.err(r.error))
                    is Try.Ok -> Poll.Ready(SinkOutcome.ready())
                }
            }
        }
    }
}
