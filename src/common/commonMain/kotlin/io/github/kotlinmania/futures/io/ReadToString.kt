// port-lint: source io/read_to_string.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC
import kotlin.text.CharacterCodingException

/**
 * Future for the [AsyncRead.readToString] method.
 */
@HiddenFromObjC
public class ReadToString(
    private val reader: AsyncRead,
    private val outBuf: StringBuilder,
) : Future<Try<Int, IoError>> {
    private val bytes = outBuf.toString().encodeToByteArray().toMutableList()
    private val tempBuf = ByteArray(8192)
    private var totalRead = 0

    init {
        outBuf.clear()
    }

    private fun finish(result: Try<Int, IoError>): Poll<Try<Int, IoError>> {
        val str =
            try {
                bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
            } catch (_: CharacterCodingException) {
                return Poll.Ready(
                    when (result) {
                        is Try.Err -> result
                        is Try.Ok -> Try.Err(IoError(IoErrorKind.InvalidData, "stream did not contain valid UTF-8"))
                    },
                )
            }
        outBuf.append(str)
        bytes.clear()
        return Poll.Ready(result)
    }

    override fun poll(context: TaskContext): Poll<Try<Int, IoError>> {
        while (true) {
            val pollRes = reader.pollRead(context, tempBuf, 0, tempBuf.size)
            when (pollRes) {
                is Poll.Pending -> {
                    return Poll.Pending
                }

                is Poll.Ready -> {
                    when (val result = pollRes.value) {
                        is Try.Err -> {
                            return finish(result)
                        }

                        is Try.Ok -> {
                            val n = result.value
                            if (n == 0) {
                                return finish(Try.Ok(totalRead))
                            }
                            for (i in 0 until n) {
                                bytes.add(tempBuf[i])
                            }
                            totalRead += n
                        }
                    }
                }
            }
        }
    }

    public interface Output

    public companion object {
        public fun new(reader: AsyncRead, buf: StringBuilder): ReadToString =
            ReadToString(reader, buf)
    }
}
