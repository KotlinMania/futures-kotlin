// port-lint: source future/try_future/into_future.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [intoFuture] method.
 */
@HiddenFromObjC
public class IntoFuture<T, E>(
    private val future: TryFuture<T, E>,
) : FusedFuture<Try<T, E>> {
    public companion object {
        internal fun <T, E> new(future: TryFuture<T, E>): IntoFuture<T, E> =
            IntoFuture(future)
    }

    override fun isTerminated(): Boolean =
        (future as? FusedFuture<*>)?.isTerminated() ?: false

    override fun poll(context: TaskContext): Poll<Try<T, E>> =
        future.poll(context)
}

/**
 * Converts this [TryFuture] into a standard [Future] yielding `Try<T, E>`.
 */
@HiddenFromObjC
public fun <T, E> TryFuture<T, E>.intoFuture(): IntoFuture<T, E> = IntoFuture.new(this)
