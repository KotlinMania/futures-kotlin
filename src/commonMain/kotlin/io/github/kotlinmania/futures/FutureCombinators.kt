// port-lint: source futures-util/src/future/mod.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Maps this future's output to a different value.
 */
@HiddenFromObjC
public fun <T, R> Future<T>.map(transform: (T) -> R): Future<R> {
    val source = this
    return object : Future<R> {
        override fun poll(context: TaskContext): Poll<R> =
            when (val p = source.poll(context)) {
                is Poll.Ready -> Poll.Ready(transform(p.value))
                is Poll.Pending -> Poll.Pending
            }
    }
}

/**
 * Future for the [fuse] combinator.
 */
@HiddenFromObjC
public class Fuse<T>(
    private var inner: Future<T>?,
) : FusedFuture<T> {
    override fun isTerminated(): Boolean = inner == null

    override fun poll(context: TaskContext): Poll<T> {
        val fut = inner ?: return Poll.Pending
        return when (val res = fut.poll(context)) {
            is Poll.Ready -> {
                inner = null
                res
            }
            is Poll.Pending -> Poll.Pending
        }
    }

    public companion object {
        public fun <T> terminated(): Fuse<T> = Fuse(null)
    }
}

/**
 * Fuses this future so that it stops yielding `Ready` after the first completion.
 */
@HiddenFromObjC
public fun <T> Future<T>.fuse(): Fuse<T> = Fuse(this)

/**
 * Chains a computation that returns another future.
 */
@HiddenFromObjC
public fun <T, R> Future<T>.then(transform: (T) -> Future<R>): Future<R> {
    val source = this
    var second: Future<R>? = null
    return object : Future<R> {
        override fun poll(context: TaskContext): Poll<R> {
            val sec = second
            if (sec != null) {
                return sec.poll(context)
            }
            return when (val p = source.poll(context)) {
                is Poll.Ready -> {
                    val nextFut = transform(p.value)
                    second = nextFut
                    nextFut.poll(context)
                }
                is Poll.Pending -> Poll.Pending
            }
        }
    }
}

/**
 * Flattens the execution of this future when the successful result is another future.
 */
@HiddenFromObjC
public fun <T> Future<Future<T>>.flatten(): Future<T> =
    this.then { it }

/**
 * Inspects this future's output when it is ready.
 */
@HiddenFromObjC
public fun <T> Future<T>.inspect(action: (T) -> Unit): Future<T> {
    val source = this
    return object : Future<T> {
        override fun poll(context: TaskContext): Poll<T> =
            when (val p = source.poll(context)) {
                is Poll.Ready -> {
                    action(p.value)
                    p
                }
                is Poll.Pending -> Poll.Pending
            }
    }
}

/**
 * Wraps this future's output in [Try.Ok] with [Unit] error type.
 */
@HiddenFromObjC
public fun <T> Future<T>.unitError(): Future<Try<T, Unit>> =
    this.map { Try.ok(it) }

/**
 * Wraps this future's output in [Try.Ok] with [Nothing] error type.
 */
@HiddenFromObjC
public fun <T> Future<T>.neverError(): Future<Try<T, Nothing>> =
    this.map { Try.ok(it) }

/**
 * Maps the `Ok` value of a [TryFuture].
 */
@HiddenFromObjC
public fun <T, E, R> TryFuture<T, E>.mapOk(transform: (T) -> R): TryFuture<R, E> {
    val source = this
    return object : TryFuture<R, E> {
        override fun poll(context: TaskContext): Poll<Try<R, E>> =
            when (val p = source.poll(context)) {
                is Poll.Ready ->
                    when (val res = p.value) {
                        is Try.Ok -> Poll.Ready(Try.ok(transform(res.value)))
                        is Try.Err -> Poll.Ready(Try.err(res.error))
                    }
                is Poll.Pending -> Poll.Pending
            }
    }
}

/**
 * Maps the `Err` value of a [TryFuture].
 */
@HiddenFromObjC
public fun <T, E, R> TryFuture<T, E>.mapErr(transform: (E) -> R): TryFuture<T, R> {
    val source = this
    return object : TryFuture<T, R> {
        override fun poll(context: TaskContext): Poll<Try<T, R>> =
            when (val p = source.poll(context)) {
                is Poll.Ready ->
                    when (val res = p.value) {
                        is Try.Ok -> Poll.Ready(Try.ok(res.value))
                        is Try.Err -> Poll.Ready(Try.err(transform(res.error)))
                    }
                is Poll.Pending -> Poll.Pending
            }
    }
}

/**
 * Chains another [TryFuture] when this future resolves to `Ok`.
 */
@HiddenFromObjC
public fun <T, E, R> TryFuture<T, E>.andThen(transform: (T) -> TryFuture<R, E>): TryFuture<R, E> {
    val source = this
    var second: TryFuture<R, E>? = null
    return object : TryFuture<R, E> {
        override fun poll(context: TaskContext): Poll<Try<R, E>> {
            val sec = second
            if (sec != null) {
                return sec.poll(context)
            }
            return when (val p = source.poll(context)) {
                is Poll.Ready ->
                    when (val res = p.value) {
                        is Try.Ok -> {
                            val next = transform(res.value)
                            second = next
                            next.poll(context)
                        }
                        is Try.Err -> Poll.Ready(Try.err(res.error))
                    }
                is Poll.Pending -> Poll.Pending
            }
        }
    }
}

/**
 * Chains another [TryFuture] when this future resolves to `Err`.
 */
@HiddenFromObjC
public fun <T, E, R> TryFuture<T, E>.orElse(transform: (E) -> TryFuture<T, R>): TryFuture<T, R> {
    val source = this
    var second: TryFuture<T, R>? = null
    return object : TryFuture<T, R> {
        override fun poll(context: TaskContext): Poll<Try<T, R>> {
            val sec = second
            if (sec != null) {
                return sec.poll(context)
            }
            return when (val p = source.poll(context)) {
                is Poll.Ready ->
                    when (val res = p.value) {
                        is Try.Ok -> Poll.Ready(Try.ok(res.value))
                        is Try.Err -> {
                            val next = transform(res.error)
                            second = next
                            next.poll(context)
                        }
                    }
                is Poll.Pending -> Poll.Pending
            }
        }
    }
}

/**
 * Unwraps the `Ok` value or computes a fallback from the `Err` value.
 */
@HiddenFromObjC
public fun <T, E> TryFuture<T, E>.unwrapOrElse(fallback: (E) -> T): Future<T> {
    val source = this
    return object : Future<T> {
        override fun poll(context: TaskContext): Poll<T> =
            when (val p = source.poll(context)) {
                is Poll.Ready ->
                    when (val res = p.value) {
                        is Try.Ok -> Poll.Ready(res.value)
                        is Try.Err -> Poll.Ready(fallback(res.error))
                    }
                is Poll.Pending -> Poll.Pending
            }
    }
}

/**
 * Inspects the `Ok` value when ready.
 */
@HiddenFromObjC
public fun <T, E> TryFuture<T, E>.inspectOk(action: (T) -> Unit): TryFuture<T, E> {
    val source = this
    return object : TryFuture<T, E> {
        override fun poll(context: TaskContext): Poll<Try<T, E>> =
            when (val p = source.poll(context)) {
                is Poll.Ready -> {
                    if (p.value is Try.Ok) {
                        action(p.value.value)
                    }
                    p
                }
                is Poll.Pending -> Poll.Pending
            }
    }
}

/**
 * Inspects the `Err` value when ready.
 */
@HiddenFromObjC
public fun <T, E> TryFuture<T, E>.inspectErr(action: (E) -> Unit): TryFuture<T, E> {
    val source = this
    return object : TryFuture<T, E> {
        override fun poll(context: TaskContext): Poll<Try<T, E>> =
            when (val p = source.poll(context)) {
                is Poll.Ready -> {
                    if (p.value is Try.Err) {
                        action(p.value.error)
                    }
                    p
                }
                is Poll.Pending -> Poll.Pending
            }
    }
}
