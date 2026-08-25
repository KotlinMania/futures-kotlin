// port-lint: source futures-task/src/noop_waker.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Create a new [Waker] which does nothing when `wake()` is called on it.
 */
@HiddenFromObjC
public fun noopWaker(): Waker = NOOP_WAKER

/**
 * Get a static reference to a [Waker] which does nothing when `wake()` is called on it.
 */
@HiddenFromObjC
public fun noopWakerRef(): Waker = NOOP_WAKER

private val NOOP_WAKER: Waker = Waker { /* no-op */ }
