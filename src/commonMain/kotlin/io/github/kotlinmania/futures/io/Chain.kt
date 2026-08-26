// port-lint: source futures-util/src/io/chain.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Reader for the [AsyncRead.chain] method.
 */
@HiddenFromObjC
public class Chain<T : AsyncRead, U : AsyncRead>(
    public val first: T,
    public val second: U,
) : AsyncRead {
    private var doneFirst = false

    public fun getRef(): Pair<T, U> = Pair(first, second)

    override fun pollRead(
        context: TaskContext,
        buf: ByteArray,
        offset: Int,
        length: Int,
    ): Poll<Try<Int, IoError>> {
        if (!doneFirst) {
            val res = first.pollRead(context, buf, offset, length)
            when (res) {
                is Poll.Pending -> return Poll.Pending
                is Poll.Ready ->
                    when (val r = res.value) {
                        is Try.Err -> return Poll.Ready(Try.err(r.error))
                        is Try.Ok -> {
                            val n = r.value
                            if (n == 0 && length > 0) {
                                doneFirst = true
                            } else {
                                return Poll.Ready(Try.ok(n))
                            }
                        }
                    }
            }
        }
        return second.pollRead(context, buf, offset, length)
    }
}
