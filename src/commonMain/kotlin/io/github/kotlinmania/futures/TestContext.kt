// port-lint: source futures-test/src/task/context.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Create a new [TaskContext] where the waker will throw an error if used.
 */
@HiddenFromObjC
public fun panicContext(): TaskContext = TaskContext(waker = panicWakerRef())

/**
 * Create a new [TaskContext] where the waker will ignore any uses.
 */
@HiddenFromObjC
public fun noopContext(): TaskContext = TaskContext(waker = noopWakerRef())
