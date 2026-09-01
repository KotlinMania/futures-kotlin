// port-lint: source io/read_to_string.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Future for the [AsyncRead.readToString] method.
 */
@HiddenFromObjC
public class ReadToString(
    private val reader: AsyncRead,
    private val outBuf: StringBuilder,
) : Future<Try<Int, IoError>> {
    private val bytes = mutableListOf<Byte>()
    private val tempBuf = ByteArray(8192)
    private var totalRead = 0

    override fun poll(context: TaskContext): Poll<Try<Int, IoError>> {
        while (true) {
            val pollRes = reader.pollRead(context, tempBuf, 0, tempBuf.size)
            when (pollRes) {
                is Poll.Pending -> return Poll.Pending
                is Poll.Ready -> {
                    when (val result = pollRes.value) {
                        is Try.Err -> return Poll.Ready(Try.err(result.error))
                        is Try.Ok -> {
                            val n = result.value
                            if (n == 0) {
                                val byteArray = ByteArray(bytes.size) { i -> bytes[i] }
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
