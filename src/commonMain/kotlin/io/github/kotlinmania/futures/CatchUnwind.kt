// port-lint: source futures-util/src/future/future/catch_unwind.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [catchUnwind] method.
 */
@HiddenFromObjC
public class CatchUnwind<T>(
    private val future: Future<T>,
) : Future<Result<T>> {
    public companion object {
        internal fun <T> new(future: Future<T>): CatchUnwind<T> = CatchUnwind(future)
    }

    override fun poll(context: TaskContext): Poll<Result<T>> =
        try {
            when (val p = future.poll(context)) {
                is Poll.Ready -> Poll.Ready(Result.success(p.value))
                Poll.Pending -> Poll.Pending
            }
        } catch (t: Throwable) {
            Poll.Ready(Result.failure(t))
        }
}

/**
 * Catches unwinding panics while polling the future.
 */
@HiddenFromObjC
public fun <T> Future<T>.catchUnwind(): CatchUnwind<T> = CatchUnwind.new(this)
