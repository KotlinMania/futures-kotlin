// port-lint: source futures-util/src/future/either.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.AsyncBufRead
import io.github.kotlinmania.futures.io.AsyncRead
import io.github.kotlinmania.futures.io.AsyncSeek
import io.github.kotlinmania.futures.io.AsyncWrite
import io.github.kotlinmania.futures.io.IoError
import io.github.kotlinmania.futures.io.SeekFrom
import kotlin.native.HiddenFromObjC


/**
 * Combines two different types into a single type.
 *
 * Useful for conditional branches where two distinct types are chosen between.
 */
@HiddenFromObjC
public sealed interface Either<out A, out B> {
    /**
     * First branch of the type.
     */
    @HiddenFromObjC
    public data class Left<out A>(
        public val value: A,
    ) : Either<A, Nothing>

    /**
     * Second branch of the type.
     */
    @HiddenFromObjC
    public data class Right<out B>(
        public val value: B,
    ) : Either<Nothing, B>

    public companion object {
        @HiddenFromObjC
        public fun <A> left(value: A): Either<A, Nothing> = Left(value)

        @HiddenFromObjC
        public fun <B> right(value: B): Either<Nothing, B> = Right(value)
    }
}

/**
 * Returns true if this is [Either.Left].
 */
@HiddenFromObjC
public val <A, B> Either<A, B>.isLeft: Boolean
    get() = this is Either.Left

/**
 * Returns true if this is [Either.Right].
 */
@HiddenFromObjC
public val <A, B> Either<A, B>.isRight: Boolean
    get() = this is Either.Right

/**
 * Returns the left value if present, or null.
 */
@HiddenFromObjC
public fun <A, B> Either<A, B>.leftOrNull(): A? =
    when (this) {
        is Either.Left -> value
        is Either.Right -> null
    }

/**
 * Returns the right value if present, or null.
 */
@HiddenFromObjC
public fun <A, B> Either<A, B>.rightOrNull(): B? =
    when (this) {
        is Either.Left -> null
        is Either.Right -> value
    }

/**
 * Fold over [Either]: handle left or right case.
 */
@HiddenFromObjC
public inline fun <A, B, R> Either<A, B>.fold(
    onLeft: (A) -> R,
    onRight: (B) -> R,
): R =
    when (this) {
        is Either.Left -> onLeft(value)
        is Either.Right -> onRight(value)
    }

/**
 * Extract the value of an either over two equivalent types.
 */
@HiddenFromObjC
public fun <T> Either<T, T>.intoInner(): T =
    when (this) {
        is Either.Left -> value
        is Either.Right -> value
    }

/**
 * Factor out a homogeneous type from an either of pairs (first element).
 */
@HiddenFromObjC
public fun <A, B, T> Either<Pair<T, A>, Pair<T, B>>.factorFirst(): Pair<T, Either<A, B>> =
    when (this) {
        is Either.Left -> Pair(value.first, Either.Left(value.second))
        is Either.Right -> Pair(value.first, Either.Right(value.second))
    }

/**
 * Factor out a homogeneous type from an either of pairs (second element).
 */
@HiddenFromObjC
public fun <A, B, T> Either<Pair<A, T>, Pair<B, T>>.factorSecond(): Pair<Either<A, B>, T> =
    when (this) {
        is Either.Left -> Pair(Either.Left(value.first), value.second)
        is Either.Right -> Pair(Either.Right(value.first), value.second)
    }

/**
 * Adapts an [Either] of two futures with the same output type into a single [FusedFuture].
 */
@HiddenFromObjC
public fun <T> Either<Future<T>, Future<T>>.asFuture(): FusedFuture<T> =
    object : FusedFuture<T> {
        private var terminated = false

        override fun isTerminated(): Boolean {
            if (terminated) return true
            return when (val e = this@asFuture) {
                is Either.Left -> (e.value as? FusedFuture<*>)?.isTerminated() ?: false
                is Either.Right -> (e.value as? FusedFuture<*>)?.isTerminated() ?: false
            }
        }

        override fun poll(context: TaskContext): Poll<T> {
            val res =
                when (val e = this@asFuture) {
                    is Either.Left -> e.value.poll(context)
                    is Either.Right -> e.value.poll(context)
                }
            if (res is Poll.Ready) {
                terminated = true
            }
            return res
        }
    }

/**
 * Adapts an [Either] of two streams with the same item type into a single [FusedStream].
 */
@HiddenFromObjC
public fun <T> Either<Stream<T>, Stream<T>>.asStream(): FusedStream<T> =
    object : FusedStream<T> {
        private var terminated = false

        override fun isTerminated(): Boolean {
            if (terminated) return true
            return when (val e = this@asStream) {
                is Either.Left -> (e.value as? FusedStream<*>)?.isTerminated() ?: false
                is Either.Right -> (e.value as? FusedStream<*>)?.isTerminated() ?: false
            }
        }

        override fun pollNext(context: TaskContext): Poll<Yield<T>> {
            val res =
                when (val e = this@asStream) {
                    is Either.Left -> e.value.pollNext(context)
                    is Either.Right -> e.value.pollNext(context)
                }
            if (res is Poll.Ready && res.value is Yield.End) {
                terminated = true
            }
            return res
        }

        override fun sizeHint(): SizeHint =
            when (val e = this@asStream) {
                is Either.Left -> e.value.sizeHint()
                is Either.Right -> e.value.sizeHint()
            }
    }

/**
 * Adapts an [Either] of two sinks into a single [Sink].
 */
@HiddenFromObjC
public fun <Item, E> Either<Sink<Item, E>, Sink<Item, E>>.asSink(): Sink<Item, E> =
    object : Sink<Item, E> {
        override fun pollReady(context: TaskContext): Poll<SinkOutcome<E>> =
            when (val e = this@asSink) {
                is Either.Left -> e.value.pollReady(context)
                is Either.Right -> e.value.pollReady(context)
            }

        override fun startSend(item: Item): SinkOutcome<E> =
            when (val e = this@asSink) {
                is Either.Left -> e.value.startSend(item)
                is Either.Right -> e.value.startSend(item)
            }

        override fun pollFlush(context: TaskContext): Poll<SinkOutcome<E>> =
            when (val e = this@asSink) {
                is Either.Left -> e.value.pollFlush(context)
                is Either.Right -> e.value.pollFlush(context)
            }

        override fun pollClose(context: TaskContext): Poll<SinkOutcome<E>> =
            when (val e = this@asSink) {
                is Either.Left -> e.value.pollClose(context)
                is Either.Right -> e.value.pollClose(context)
            }
    }

/**
 * Adapts an [Either] of two async readers into a single [AsyncRead].
 */
@HiddenFromObjC
public fun Either<AsyncRead, AsyncRead>.asAsyncRead(): AsyncRead =
    object : AsyncRead {
        override fun pollRead(
            context: TaskContext,
            buf: ByteArray,
            offset: Int,
            length: Int,
        ): Poll<Try<Int, IoError>> =
            when (val e = this@asAsyncRead) {
                is Either.Left -> e.value.pollRead(context, buf, offset, length)
                is Either.Right -> e.value.pollRead(context, buf, offset, length)
            }
    }

/**
 * Adapts an [Either] of two async writers into a single [AsyncWrite].
 */
@HiddenFromObjC
public fun Either<AsyncWrite, AsyncWrite>.asAsyncWrite(): AsyncWrite =
    object : AsyncWrite {
        override fun pollWrite(
            context: TaskContext,
            buf: ByteArray,
            offset: Int,
            length: Int,
        ): Poll<Try<Int, IoError>> =
            when (val e = this@asAsyncWrite) {
                is Either.Left -> e.value.pollWrite(context, buf, offset, length)
                is Either.Right -> e.value.pollWrite(context, buf, offset, length)
            }

        override fun pollFlush(context: TaskContext): Poll<Try<Unit, IoError>> =
            when (val e = this@asAsyncWrite) {
                is Either.Left -> e.value.pollFlush(context)
                is Either.Right -> e.value.pollFlush(context)
            }

        override fun pollClose(context: TaskContext): Poll<Try<Unit, IoError>> =
            when (val e = this@asAsyncWrite) {
                is Either.Left -> e.value.pollClose(context)
                is Either.Right -> e.value.pollClose(context)
            }
    }

/**
 * Adapts an [Either] of two async seekable streams into a single [AsyncSeek].
 */
@HiddenFromObjC
public fun Either<AsyncSeek, AsyncSeek>.asAsyncSeek(): AsyncSeek =
    object : AsyncSeek {
        override fun pollSeek(
            context: TaskContext,
            pos: SeekFrom,
        ): Poll<Try<Long, IoError>> =
            when (val e = this@asAsyncSeek) {
                is Either.Left -> e.value.pollSeek(context, pos)
                is Either.Right -> e.value.pollSeek(context, pos)
            }
    }

/**
 * Adapts an [Either] of two async buffered readers into a single [AsyncBufRead].
 */
@HiddenFromObjC
public fun Either<AsyncBufRead, AsyncBufRead>.asAsyncBufRead(): AsyncBufRead =
    object : AsyncBufRead {
        override fun pollRead(
            context: TaskContext,
            buf: ByteArray,
            offset: Int,
            length: Int,
        ): Poll<Try<Int, IoError>> =
            when (val e = this@asAsyncBufRead) {
                is Either.Left -> e.value.pollRead(context, buf, offset, length)
                is Either.Right -> e.value.pollRead(context, buf, offset, length)
            }

        override fun pollFillBuf(context: TaskContext): Poll<Try<ByteArray, IoError>> =
            when (val e = this@asAsyncBufRead) {
                is Either.Left -> e.value.pollFillBuf(context)
                is Either.Right -> e.value.pollFillBuf(context)
            }

        override fun consume(amt: Int) {
            when (val e = this@asAsyncBufRead) {
                is Either.Left -> e.value.consume(amt)
                is Either.Right -> e.value.consume(amt)
            }
        }
    }

