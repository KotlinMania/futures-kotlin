// port-lint: source futures-test/src/task/panic_waker.rs
package io.github.kotlinmania.futures

private val PANIC_WAKER_INSTANCE: Waker =
    Waker {
        error("should not be woken")
    }

/**
 * Create a new [Waker] which will throw an error when `wake()` is called on it.
 */
public fun panicWaker(): Waker = PANIC_WAKER_INSTANCE

/**
 * Get a global reference to a singleton [Waker] which throws an error when woken.
 */
public fun panicWakerRef(): Waker = PANIC_WAKER_INSTANCE
