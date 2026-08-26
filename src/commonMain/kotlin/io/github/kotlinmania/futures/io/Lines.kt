// port-lint: source futures-util/src/io/lines.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.Stream
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import io.github.kotlinmania.futures.Yield
import kotlin.native.HiddenFromObjC

/**
 * Stream for the [AsyncBufRead.lines] method.
 */
@HiddenFromObjC
public class Lines<R : AsyncBufRead>(
    private val reader: R,
) : Stream<Try<String, IoError>> {
    private val lineBuf = StringBuilder()
    private var currentReadLine: ReadLine? = null

    override fun pollNext(context: TaskContext): Poll<Yield<Try<String, IoError>>> {
        val readLine = currentReadLine ?: ReadLine(reader, lineBuf).also { currentReadLine = it }
        val pollRes = readLine.poll(context)
        return when (pollRes) {
            is Poll.Pending -> Poll.Pending
            is Poll.Ready -> {
                currentReadLine = null
                when (val result = pollRes.value) {
                    is Try.Err -> Poll.Ready(Yield.value(Try.err(result.error)))
                    is Try.Ok -> {
                        val n = result.value
                        if (n == 0 && lineBuf.isEmpty()) {
                            Poll.Ready(Yield.end())
                        } else {
                            var line = lineBuf.toString()
                            lineBuf.clear()
                            if (line.endsWith("\n")) {
                                line = line.substring(0, line.length - 1)
                                if (line.endsWith("\r")) {
                                    line = line.substring(0, line.length - 1)
                                }
                            }
                            Poll.Ready(Yield.value(Try.ok(line)))
                        }
                    }
                }
            }
        }
    }
}
