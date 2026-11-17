// port-lint: source future/maybe_done.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * A future that may have completed.
 *
 * This is created by the [maybeDone] function.
 */
@HiddenFromObjC
public class MaybeDone<T> internal constructor(
    private var future: Future<T>?,
    private var output: T?,
    private var isGone: Boolean,
) : FusedFuture<Unit> {
    /**
     * Returns the output if the inner future has completed and [takeOutput] has not yet been called.
     */
    public fun outputOrNull(): T? = if (future == null && !isGone) output else null

    /**
     * Attempt to take the output of a [MaybeDone] without driving it towards completion.
     */
    public fun takeOutput(): T? =
        if (future == null && !isGone) {
            val res = output
            output = null
            isGone = true
            res
        } else {
            null
        }

    override fun isTerminated(): Boolean = future == null

    override fun poll(context: TaskContext): Poll<Unit> {
        val fut = future
        if (fut != null) {
            when (val p = fut.poll(context)) {
                is Poll.Ready -> {
                    future = null
                    output = p.value
                    return Poll.Ready(Unit)
                }
                is Poll.Pending -> return Poll.Pending
            }
        }

        if (isGone) {
            error("MaybeDone polled after value taken")
        }

        return Poll.Ready(Unit)
    }

    public companion object {
        @HiddenFromObjC
        public fun <T> inProgress(future: Future<T>): MaybeDone<T> = MaybeDone(future, null, false)

        @HiddenFromObjC
        public fun <T> done(output: T): MaybeDone<T> = MaybeDone(null, output, false)

        @HiddenFromObjC
        public fun <T> gone(): MaybeDone<T> = MaybeDone(null, null, true)
    }
}

/**
 * Wraps a future into a [MaybeDone].
 */
@HiddenFromObjC
public fun <T> maybeDone(future: Future<T>): MaybeDone<T> = MaybeDone.inProgress(future)
