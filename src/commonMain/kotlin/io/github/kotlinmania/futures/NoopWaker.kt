// port-lint: source futures-task/src/noop_waker.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * A [Waker] that does nothing when notified.
 *
 * Mirrors upstream `futures_task::noop_waker`. The upstream implementation
 * constructs a [`RawWaker`](https://doc.rust-lang.org/core/task/struct.RawWaker.html)
 * around a vtable of no-op function pointers; the Kotlin [Waker] is already
 * a [fun interface], so the equivalent is simply an empty lambda.
 */
@HiddenFromObjC
public fun noopWaker(): Waker = NOOP_WAKER

/**
 * A shared static [Waker] reference that does nothing when notified.
 *
 * Mirrors upstream `futures_task::noop_waker_ref`. The upstream pointer-cast
 * from `&'static RawWaker` to `&'static Waker` exists to bypass `Waker`'s
 * non-`Sync` field; Kotlin objects are heap-managed and the [Waker] SAM has
 * no equivalent constraint, so we expose the same singleton instance
 * returned by [noopWaker].
 */
@HiddenFromObjC
public fun noopWakerRef(): Waker = NOOP_WAKER

private val NOOP_WAKER: Waker = Waker { /* no-op */ }
