// port-lint: source futures-task/src/arc_wake.rs
package io.github.kotlinmania.futures

/**
 * A way of waking up a specific task.
 *
 * By implementing this interface, types can be converted into [Waker] objects.
 * Those Wakers can be used to signal executors that a task it owns
 * is ready to be polled again.
 */
public fun interface ArcWake {
    /**
     * Indicates that the associated task is ready to make progress and should
     * be polled.
     */
    public fun wakeByRef()

    /**
     * Indicates that the associated task is ready to make progress and should
     * be polled.
     */
    public fun wake() {
        wakeByRef()
    }
}
