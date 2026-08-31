// port-lint: source futures-task/src/waker.rs
package io.github.kotlinmania.futures

/**
 * Creates a [Waker] from an [ArcWake] instance.
 *
 * The returned [Waker] will call [ArcWake.wakeByRef] if awoken.
 */
public fun waker(wake: ArcWake): Waker = Waker { wake.wakeByRef() }
