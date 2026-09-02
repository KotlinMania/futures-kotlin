// port-lint: source futures-util/src/fns.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Trait for callable functions of one argument.
 */
@HiddenFromObjC
public fun interface Fn1<in A, out R> {
    public operator fun invoke(arg: A): R
}

/**
 * Returns [Try.Ok] containing the given argument.
 */
@HiddenFromObjC
public class OkFn<E> : Fn1<Any?, Try<Any?, E>> {
    override fun invoke(arg: Any?): Try<Any?, E> = Try.ok(arg)

    override fun toString(): String = "Ok"
}

@HiddenFromObjC
public fun <E> okFn(): OkFn<E> = OkFn()

/**
 * Composes two functions [first] and [second].
 */
@HiddenFromObjC
public class ChainFn<F : (A) -> B, G : (B) -> C, A, B, C>(
    private val first: F,
    private val second: G,
) : Fn1<A, C> {
    override fun invoke(arg: A): C = second(first(arg))

    override fun toString(): String = "ChainFn($first, $second)"
}

@HiddenFromObjC
public fun <A, B, C> chainFn(
    first: (A) -> B,
    second: (B) -> C,
): ChainFn<(A) -> B, (B) -> C, A, B, C> = ChainFn(first, second)

/**
 * Merges a [Try] with same Ok and Err type into a single value.
 */
@HiddenFromObjC
public class MergeResultFn<T> : Fn1<Try<T, T>, T> {
    override fun invoke(arg: Try<T, T>): T =
        when (arg) {
            is Try.Ok -> arg.value
            is Try.Err -> arg.error
        }

    override fun toString(): String = "merge_result"
}

@HiddenFromObjC
public fun <T> mergeResultFn(): MergeResultFn<T> = MergeResultFn()

/**
 * Inspects a value by calling [action] and returns the original value.
 */
@HiddenFromObjC
public class InspectFn<F : (A) -> Unit, A>(
    private val action: F,
) : Fn1<A, A> {
    override fun invoke(arg: A): A {
        action(arg)
        return arg
    }

    override fun toString(): String = "InspectFn($action)"
}

@HiddenFromObjC
public fun <A> inspectFn(action: (A) -> Unit): InspectFn<(A) -> Unit, A> = InspectFn(action)

/**
 * Maps the [Try.Ok] variant of a [Try] using [mapper].
 */
@HiddenFromObjC
public class MapOkFn<F : (T) -> R, T, R, E>(
    private val mapper: F,
) : Fn1<Try<T, E>, Try<R, E>> {
    override fun invoke(arg: Try<T, E>): Try<R, E> =
        when (arg) {
            is Try.Ok -> Try.ok(mapper(arg.value))
            is Try.Err -> Try.err(arg.error)
        }

    override fun toString(): String = "MapOkFn($mapper)"
}

@HiddenFromObjC
public fun <T, R, E> mapOkFn(mapper: (T) -> R): MapOkFn<(T) -> R, T, R, E> = MapOkFn(mapper)

/**
 * Maps the [Try.Err] variant of a [Try] using [mapper].
 */
@HiddenFromObjC
public class MapErrFn<F : (E) -> R, T, E, R>(
    private val mapper: F,
) : Fn1<Try<T, E>, Try<T, R>> {
    override fun invoke(arg: Try<T, E>): Try<T, R> =
        when (arg) {
            is Try.Ok -> Try.ok(arg.value)
            is Try.Err -> Try.err(mapper(arg.error))
        }

    override fun toString(): String = "MapErrFn($mapper)"
}

@HiddenFromObjC
public fun <T, E, R> mapErrFn(mapper: (E) -> R): MapErrFn<(E) -> R, T, E, R> = MapErrFn(mapper)

/**
 * Inspects the [Try.Ok] variant of a [Try].
 */
@HiddenFromObjC
public class InspectOkFn<F : (T) -> Unit, T, E>(
    private val action: F,
) : Fn1<Try<T, E>, Unit> {
    override fun invoke(arg: Try<T, E>) {
        if (arg is Try.Ok) {
            action(arg.value)
        }
    }

    override fun toString(): String = "InspectOkFn($action)"
}

@HiddenFromObjC
public fun <T, E> inspectOkFn(action: (T) -> Unit): InspectOkFn<(T) -> Unit, T, E> = InspectOkFn(action)

/**
 * Inspects the [Try.Err] variant of a [Try].
 */
@HiddenFromObjC
public class InspectErrFn<F : (E) -> Unit, T, E>(
    private val action: F,
) : Fn1<Try<T, E>, Unit> {
    override fun invoke(arg: Try<T, E>) {
        if (arg is Try.Err) {
            action(arg.error)
        }
    }

    override fun toString(): String = "InspectErrFn($action)"
}

@HiddenFromObjC
public fun <T, E> inspectErrFn(action: (E) -> Unit): InspectErrFn<(E) -> Unit, T, E> = InspectErrFn(action)

/**
 * Maps ok or else.
 */
@HiddenFromObjC
public fun <T, R, E> mapOkOrElseFn(
    f: (T) -> R,
    g: (E) -> R,
): (Try<T, E>) -> R = { tryVal ->
    when (tryVal) {
        is Try.Ok -> f(tryVal.value)
        is Try.Err -> g(tryVal.error)
    }
}

/**
 * Unwraps Ok value or computes from Err.
 */
@HiddenFromObjC
public class UnwrapOrElseFn<F : (E) -> T, T, E>(
    private val fallback: F,
) : Fn1<Try<T, E>, T> {
    override fun invoke(arg: Try<T, E>): T =
        when (arg) {
            is Try.Ok -> arg.value
            is Try.Err -> fallback(arg.error)
        }

    override fun toString(): String = "UnwrapOrElseFn($fallback)"
}

@HiddenFromObjC
public fun <T, E> unwrapOrElseFn(fallback: (E) -> T): UnwrapOrElseFn<(E) -> T, T, E> =
    UnwrapOrElseFn(fallback)
