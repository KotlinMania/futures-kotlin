// port-lint: source futures-util/src/io/read_until.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Future for the [AsyncBufRead.readUntil] method.
 */
@HiddenFromObjC
public class ReadUntil(
    private val reader: AsyncBufRead,
    private val byte: Byte,
    private val outBuf: MutableList<Byte>,
) : Future<Try<Int, IoError>> {
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
                                return Poll.Ready(Try.ok(totalRead))
                            }
                            var foundIndex = -1
                            for (i in available.indices) {
                                if (available[i] == byte) {
                                    foundIndex = i
                                    break
                                }
                            }
                            if (foundIndex >= 0) {
                                for (i in 0..foundIndex) {
                                    outBuf.add(available[i])
                                }
                                val used = foundIndex + 1
                                reader.consume(used)
                                totalRead += used
                                return Poll.Ready(Try.ok(totalRead))
                            } else {
                                for (i in available.indices) {
                                    outBuf.add(available[i])
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
}
