// port-lint: source futures-test/src/task/panic_waker.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

private val PANIC_WAKER_INSTANCE: Waker =
    Waker {
        error("should not be woken")
    }

/**
 * Create a new [Waker] which will throw an error when `wake()` is called on it.
 */
@HiddenFromObjC
public fun panicWaker(): Waker = PANIC_WAKER_INSTANCE

/**
 * Get a global reference to a singleton [Waker] which throws an error when woken.
 */
@HiddenFromObjC
public fun panicWakerRef(): Waker = PANIC_WAKER_INSTANCE
