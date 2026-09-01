// port-lint: source io/chain.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Reader for the [chain] method.
 */
@HiddenFromObjC
public class Chain<T : AsyncRead, U : AsyncRead>(
    public val first: T,
    public val second: U,
) : AsyncBufRead {
    private var doneFirst = false

    public companion object {
        public fun <T : AsyncRead, U : AsyncRead> new(first: T, second: U): Chain<T, U> =
            Chain(first, second)
    }

    /**
     * Gets references to the underlying readers in this [Chain].
     */
    public fun getRef(): Pair<T, U> = Pair(first, second)

    /**
     * Gets mutable references to the underlying readers in this [Chain].
     */
    public fun getMut(): Pair<T, U> = Pair(first, second)

    /**
     * Gets pinned mutable references to the underlying readers in this [Chain].
     */
    public fun getPinMut(): Pair<T, U> = Pair(first, second)

    /**
     * Consumes the [Chain], returning the wrapped readers.
     */
    public fun intoInner(): Pair<T, U> = Pair(first, second)

    public fun fmt(): String = "Chain"


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

    override fun pollFillBuf(context: TaskContext): Poll<Try<ByteArray, IoError>> {
        if (!doneFirst) {
            val bufReader = first as? AsyncBufRead
                ?: return Poll.Ready(Try.err(IoError(IoErrorKind.Other, "first reader does not implement AsyncBufRead")))
            when (val res = bufReader.pollFillBuf(context)) {
                is Poll.Pending -> return Poll.Pending
                is Poll.Ready ->
                    when (val r = res.value) {
                        is Try.Err -> return Poll.Ready(Try.err(r.error))
                        is Try.Ok -> {
                            if (r.value.isEmpty()) {
                                doneFirst = true
                            } else {
                                return Poll.Ready(Try.ok(r.value))
                            }
                        }
                    }
            }
        }
        val bufReader = second as? AsyncBufRead
            ?: return Poll.Ready(Try.err(IoError(IoErrorKind.Other, "second reader does not implement AsyncBufRead")))
        return bufReader.pollFillBuf(context)
    }

    override fun consume(amt: Int) {
        if (!doneFirst) {
            (first as? AsyncBufRead)?.consume(amt)
        } else {
            (second as? AsyncBufRead)?.consume(amt)
        }
    }

    override fun toString(): String = "Chain(first=$first, second=$second, doneFirst=$doneFirst)"
}
