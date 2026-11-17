// port-lint: source futures-test/src/task/context.rs
package io.github.kotlinmania.futures

/**
 * Create a new [TaskContext] where the waker will throw an error if used.
 */
public fun panicContext(): TaskContext = TaskContext(waker = panicWakerRef())

/**
 * Create a new [TaskContext] where the waker will ignore any uses.
 */
public fun noopContext(): TaskContext = TaskContext(waker = noopWakerRef())
