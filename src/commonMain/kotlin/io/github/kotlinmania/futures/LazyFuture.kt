// port-lint: source future/lazy.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * A future that allows delayed execution of a closure.
 *
 * The provided closure is only run once the future is polled.
 */
@HiddenFromObjC
public class Lazy<out R>(
    private var factory: ((TaskContext) -> R)?,
) : FusedFuture<R> {
    override fun isTerminated(): Boolean = factory == null

    override fun poll(context: TaskContext): Poll<R> {
        val f = factory ?: error("Lazy polled after completion")
        factory = null
        return Poll.Ready(f(context))
    }
}

/**
 * Creates a new future that allows delayed execution of a closure.
 */
@HiddenFromObjC
public fun <R> lazyFuture(factory: (TaskContext) -> R): Lazy<R> = Lazy(factory)
