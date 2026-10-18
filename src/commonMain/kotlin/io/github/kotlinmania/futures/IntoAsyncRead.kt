// port-lint: source stream/try_stream/into_async_read.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.AsyncBufRead
import io.github.kotlinmania.futures.io.AsyncRead
import io.github.kotlinmania.futures.io.IoError
import kotlin.math.min
import kotlin.native.HiddenFromObjC

private sealed class ReadState {
    data class Ready(
        val chunk: ByteArray,
        var chunkStart: Int,
    ) : ReadState()

    data object PendingChunk : ReadState()

    data object Eof : ReadState()
}

/**
 * Reader for the [intoAsyncRead] method.
 */
@HiddenFromObjC
public class IntoAsyncRead<St : TryStream<ByteArray, IoError>>(
    private val stream: St,
) : AsyncBufRead,
    AsyncRead {
    private var state: ReadState = ReadState.PendingChunk

    public companion object {
        public fun <St : TryStream<ByteArray, IoError>> new(stream: St): IntoAsyncRead<St> =
            IntoAsyncRead(stream)
    }

    override fun pollRead(
        context: TaskContext,
        buf: ByteArray,
        offset: Int,
        length: Int,
    ): Poll<Try<Int, IoError>> {
        while (true) {
            when (val s = state) {
                is ReadState.Ready -> {
                    val remaining = s.chunk.size - s.chunkStart
                    val toCopy = min(length, remaining)
                    s.chunk.copyInto(buf, destinationOffset = offset, startIndex = s.chunkStart, endIndex = s.chunkStart + toCopy)
                    s.chunkStart += toCopy
                    if (s.chunkStart >= s.chunk.size) {
                        state = ReadState.PendingChunk
                    }
                    return Poll.Ready(Try.ok(toCopy))
                }
                is ReadState.PendingChunk -> {
                    when (val poll = stream.pollNext(context)) {
                        is Poll.Pending -> return Poll.Pending
                        is Poll.Ready -> {
                            when (val y = poll.value) {
                                is Yield.Value -> {
                                    when (val res = y.value) {
                                        is Try.Ok -> {
                                            val chunk = res.value
                                            if (chunk.isNotEmpty()) {
                                                state = ReadState.Ready(chunk, 0)
                                            }
                                        }
                                        is Try.Err -> {
                                            state = ReadState.Eof
                                            return Poll.Ready(Try.err(res.error))
                                        }
                                    }
                                }
                                Yield.End -> {
                                    state = ReadState.Eof
                                    return Poll.Ready(Try.ok(0))
                                }
                            }
                        }
                    }
                }
                is ReadState.Eof -> return Poll.Ready(Try.ok(0))
            }
        }
    }

    override fun pollFillBuf(context: TaskContext): Poll<Try<ByteArray, IoError>> {
        while (state is ReadState.PendingChunk) {
            when (val poll = stream.pollNext(context)) {
                is Poll.Pending -> return Poll.Pending
                is Poll.Ready -> {
                    when (val y = poll.value) {
                        is Yield.Value -> {
                            when (val res = y.value) {
                                is Try.Ok -> {
                                    val chunk = res.value
                                    if (chunk.isNotEmpty()) {
                                        state = ReadState.Ready(chunk, 0)
                                    }
                                }
                                is Try.Err -> {
                                    state = ReadState.Eof
                                    return Poll.Ready(Try.err(res.error))
                                }
                            }
                        }
                        Yield.End -> {
                            state = ReadState.Eof
                            return Poll.Ready(Try.ok(ByteArray(0)))
                        }
                    }
                }
            }
        }

        return when (val s = state) {
            is ReadState.Ready -> {
                val available = s.chunk.copyOfRange(s.chunkStart, s.chunk.size)
                Poll.Ready(Try.ok(available))
            }
            else -> Poll.Ready(Try.ok(ByteArray(0)))
        }
    }

    override fun consume(amt: Int) {
        if (amt <= 0) return
        when (val s = state) {
            is ReadState.Ready -> {
                s.chunkStart += amt
                if (s.chunkStart >= s.chunk.size) {
                    state = ReadState.PendingChunk
                }
            }
            else -> {}
        }
    }
}
