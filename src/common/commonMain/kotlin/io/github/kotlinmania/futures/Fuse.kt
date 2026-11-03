// port-lint: source future/future/fuse.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [fuse] method.
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
        internal fun <T> new(f: Future<T>): Fuse<T> = Fuse(f)

        /**
         * Creates a new `Fuse`-wrapped future which is already terminated.
         */
        public fun <T> terminated(): Fuse<T> = Fuse(null)
    }
}

/**
 * Fuses this future so that it stops yielding `Ready` after the first completion.
 */
@HiddenFromObjC
public fun <T> Future<T>.fuse(): Fuse<T> = Fuse(this)
