// port-lint: source futures-task/src/waker_ref.rs
package io.github.kotlinmania.futures

/**
 * A [Waker] reference wrapper that provides access to a [Waker].
 */
public class WakerRef(
    public val waker: Waker,
) {
    /**
     * Indicates that the associated task is ready to make progress and should be polled.
     */
    public fun wakeByRef() {
        waker.wakeByRef()
    }

    /**
     * Indicates that the associated task is ready to make progress and should be polled.
     */
    public fun wake() {
        waker.wake()
    }

    public companion object {
        /**
         * Create a new [WakerRef] from a [Waker] reference.
         */
        public fun new(waker: Waker): WakerRef = WakerRef(waker)

        /**
         * Create a new [WakerRef] from an unowned [Waker].
         */
        public fun newUnowned(waker: Waker): WakerRef = WakerRef(waker)
    }
}

/**
 * Creates a reference to a [Waker] from an [ArcWake] instance.
 *
 * The resulting [WakerRef] will call [ArcWake.wake] if awoken.
 */
public fun wakerRef(wake: ArcWake): WakerRef = WakerRef(waker(wake))
