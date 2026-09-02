// port-lint: source futures-test/src/future/pending_once.rs
@file:OptIn(
    kotlin.experimental.ExperimentalObjCRefinement::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
)

package io.github.kotlinmania.futures

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.native.HiddenFromObjC

/**
 * Combinator that guarantees one [Poll.pending] before polling its inner future.
 */
@HiddenFromObjC
public class PendingOnce<T>(
    private val future: Future<T>,
) : Future<T>,
    FusedFuture<T> {
    private val polledBefore = AtomicBoolean(false)

    override fun poll(context: TaskContext): Poll<T> {
        return if (polledBefore.load()) {
            future.poll(context)
        } else {
            polledBefore.store(true)
            context.waker.wakeByRef()
            Poll.pending()
        }
    }

    override fun isTerminated(): Boolean =
        polledBefore.load() && (future as? FusedFuture<*>)?.isTerminated() ?: false
}

/**
 * Wraps a future so that it returns [Poll.pending] exactly once before delegating.
 */
@HiddenFromObjC
public fun <T> Future<T>.pendingOnce(): PendingOnce<T> = PendingOnce(this)
