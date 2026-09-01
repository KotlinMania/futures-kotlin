// port-lint: source future/try_maybe_done.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * A future that may have completed with an error.
 *
 * This is created by the [tryMaybeDone] function.
 */
@HiddenFromObjC
public class TryMaybeDone<T, E> internal constructor(
    private var future: Future<Try<T, E>>?,
    private var output: T?,
    private var isGone: Boolean,
) : FusedFuture<Try<Unit, E>> {
    /**
     * Returns the output if the inner future has completed successfully and [takeOutput] has not yet been called.
     */
    public fun outputOrNull(): T? = if (future == null && !isGone) output else null

    /**
     * Attempt to take the output of a [TryMaybeDone] without driving it towards completion.
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

    override fun poll(context: TaskContext): Poll<Try<Unit, E>> {
        val fut = future
        if (fut != null) {
            when (val p = fut.poll(context)) {
                is Poll.Ready -> {
                    when (val v = p.value) {
                        is Try.Ok -> {
                            future = null
                            output = v.value
                            return Poll.Ready(Try.Ok(Unit))
                        }
                        is Try.Err -> {
                            future = null
                            isGone = true
                            return Poll.Ready(Try.Err(v.error))
                        }
                    }
                }
                is Poll.Pending -> return Poll.Pending
            }
        }

        if (isGone) {
            error("TryMaybeDone polled after value taken")
        }

        return Poll.Ready(Try.Ok(Unit))
    }

    public companion object {
        @HiddenFromObjC
        public fun <T, E> inProgress(future: Future<Try<T, E>>): TryMaybeDone<T, E> =
            TryMaybeDone(future, null, false)

        @HiddenFromObjC
        public fun <T, E> done(output: T): TryMaybeDone<T, E> =
            TryMaybeDone(null, output, false)

        @HiddenFromObjC
        public fun <T, E> gone(): TryMaybeDone<T, E> =
            TryMaybeDone(null, null, true)
    }
}

/**
 * Wraps a future into a [TryMaybeDone].
 */
@HiddenFromObjC
public fun <T, E> tryMaybeDone(future: Future<Try<T, E>>): TryMaybeDone<T, E> =
    TryMaybeDone.inProgress(future)
