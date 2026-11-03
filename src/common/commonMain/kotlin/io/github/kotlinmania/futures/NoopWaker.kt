// port-lint: source futures-task/src/noop_waker.rs
package io.github.kotlinmania.futures

/**
 * Create a new [Waker] which does nothing when `wake()` is called on it.
 */
public fun noopWaker(): Waker = NOOP_WAKER

/**
 * Get a static reference to a [Waker] which does nothing when `wake()` is called on it.
 */
public fun noopWakerRef(): Waker = NOOP_WAKER

private val NOOP_WAKER: Waker = Waker { /* no-op */ }
