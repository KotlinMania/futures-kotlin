// port-lint: source futures-util/src/io/read_to_end.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Future for the [AsyncRead.readToEnd] method.
 */
@HiddenFromObjC
public class ReadToEnd(
    private val reader: AsyncRead,
    private val outBuf: MutableList<Byte>,
) : Future<Try<Int, IoError>> {
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
                                return Poll.Ready(Try.ok(totalRead))
                            }
                            for (i in 0 until n) {
                                outBuf.add(tempBuf[i])
                            }
                            totalRead += n
                        }
                    }
                }
            }
        }
    }
}
