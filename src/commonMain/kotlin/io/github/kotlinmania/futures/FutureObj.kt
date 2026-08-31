// port-lint: source futures-task/src/future_obj.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * A custom object wrapper for polling local futures.
 */
@HiddenFromObjC
public class LocalFutureObj<out T>(
    private val future: Future<T>,
) : Future<T> {
    override fun poll(context: TaskContext): Poll<T> = future.poll(context)

    /**
     * Converts the [LocalFutureObj] into a [FutureObj].
     */
    public fun intoFutureObj(): FutureObj<T> = FutureObj(future)

    public companion object {
        /**
         * Create a [LocalFutureObj] from a [Future].
         */
        public fun <T> new(future: Future<T>): LocalFutureObj<T> = LocalFutureObj(future)
    }
}

/**
 * A custom object wrapper for polling futures.
 */
@HiddenFromObjC
public class FutureObj<out T>(
    private val future: Future<T>,
) : Future<T> {
    override fun poll(context: TaskContext): Poll<T> = future.poll(context)

    /**
     * Converts the [FutureObj] into a [LocalFutureObj].
     */
    public fun intoLocalFutureObj(): LocalFutureObj<T> = LocalFutureObj(future)

    public companion object {
        /**
         * Create a [FutureObj] from a [Future].
         */
        public fun <T> new(future: Future<T>): FutureObj<T> = FutureObj(future)
    }
}

/**
 * Custom representation interface for future objects.
 */
@HiddenFromObjC
public interface UnsafeFutureObj<out T> {
    /**
     * Convert an owned instance into a [Future].
     */
    public fun intoRaw(): Future<T>
}
