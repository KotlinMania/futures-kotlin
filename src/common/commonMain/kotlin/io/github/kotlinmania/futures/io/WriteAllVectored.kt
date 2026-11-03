// port-lint: source io/write_all_vectored.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Future for the [AsyncWriteExt.writeAllVectored] method.
 */
@HiddenFromObjC
public class WriteAllVectored(
    private val writer: AsyncWrite,
    private val bufs: MutableList<IoSlice>,
) : Future<Try<Unit, IoError>> {
    public interface Output

    public companion object {
        public fun new(writer: AsyncWrite, bufs: MutableList<IoSlice>): WriteAllVectored {
            IoSlice.advanceSlices(bufs, 0)
            return WriteAllVectored(writer, bufs)
        }
    }

    override fun poll(context: TaskContext): Poll<Try<Unit, IoError>> {
        while (bufs.isNotEmpty()) {
            when (val pollRes = writer.pollWriteVectored(context, bufs)) {
                is Poll.Pending -> return Poll.Pending
                is Poll.Ready -> {
                    when (val result = pollRes.value) {
                        is Try.Err -> return Poll.Ready(Try.err(result.error))
                        is Try.Ok -> {
                            val n = result.value
                            if (n == 0) {
                                return Poll.Ready(Try.err(IoError(IoErrorKind.WriteZero, "failed to write whole buffer")))
                            } else {
                                IoSlice.advanceSlices(bufs, n)
                            }
                        }
                    }
                }
            }
        }
        return Poll.Ready(Try.ok(Unit))
    }
}
