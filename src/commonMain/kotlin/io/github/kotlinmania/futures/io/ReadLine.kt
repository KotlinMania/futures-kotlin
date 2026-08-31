// port-lint: source futures-util/src/io/read_line.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Future for the [AsyncBufRead.readLine] method.
 */
@HiddenFromObjC
public class ReadLine(
    private val reader: AsyncBufRead,
    private val outBuf: StringBuilder,
) : Future<Try<Int, IoError>> {
    private val readBytes = mutableListOf<Byte>()
    private var totalRead: Int = 0

    override fun poll(context: TaskContext): Poll<Try<Int, IoError>> {
        while (true) {
            val fillRes = reader.pollFillBuf(context)
            when (fillRes) {
                is Poll.Pending -> return Poll.Pending
                is Poll.Ready -> {
                    when (val result = fillRes.value) {
                        is Try.Err -> return Poll.Ready(Try.err(result.error))
                        is Try.Ok -> {
                            val available = result.value
                            if (available.isEmpty()) {
                                return finishAndDecode()
                            }
                            var foundNewline = -1
                            for (i in available.indices) {
                                if (available[i] == 0x0A.toByte()) {
                                    foundNewline = i
                                    break
                                }
                            }
                            if (foundNewline >= 0) {
                                for (i in 0..foundNewline) {
                                    readBytes.add(available[i])
                                }
                                val used = foundNewline + 1
                                reader.consume(used)
                                totalRead += used
                                return finishAndDecode()
                            } else {
                                for (i in available.indices) {
                                    readBytes.add(available[i])
                                }
                                val used = available.size
                                reader.consume(used)
                                totalRead += used
                            }
                        }
                    }
                }
            }
        }
    }

    private fun finishAndDecode(): Poll<Try<Int, IoError>> {
        val byteArray = ByteArray(readBytes.size) { i -> readBytes[i] }
        return try {
            val str = byteArray.decodeToString(throwOnInvalidSequence = true)
            outBuf.append(str)
            Poll.Ready(Try.ok(totalRead))
        } catch (_: Exception) {
            Poll.Ready(
                Try.err(
                    IoError(
                        IoErrorKind.InvalidData,
                        "stream did not contain valid UTF-8",
                    ),
                ),
            )
        }
    }

    public interface Output

    public fun drop() {
        // Drop lifecycle hook matching upstream
    }

    public companion object {
        public fun new(reader: AsyncBufRead, buf: StringBuilder): ReadLine =
            ReadLine(reader, buf)

        public fun readLineInternal(
            reader: AsyncBufRead,
            context: TaskContext,
            buf: StringBuilder,
        ): Poll<Try<Int, IoError>> = ReadLine(reader, buf).poll(context)
    }
}

