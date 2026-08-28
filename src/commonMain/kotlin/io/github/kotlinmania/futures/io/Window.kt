// port-lint: source futures-util/src/io/window.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import kotlin.native.HiddenFromObjC

/**
 * An owned window around an underlying byte buffer.
 */
@HiddenFromObjC
public class Window(
    public val inner: ByteArray,
    start: Int = 0,
    end: Int = inner.size,
) {
    private var rangeStart: Int = start
    private var rangeEnd: Int = end

    init {
        require(rangeStart in 0..inner.size) { "start out of bounds: $rangeStart" }
        require(rangeEnd in 0..inner.size) { "end out of bounds: $rangeEnd" }
        require(rangeStart <= rangeEnd) { "start ($rangeStart) must not exceed end ($rangeEnd)" }
    }

    /**
     * Gets the starting index of this window into the underlying buffer.
     */
    public fun start(): Int = rangeStart

    /**
     * Gets the end index of this window into the underlying buffer.
     */
    public fun end(): Int = rangeEnd

    /**
     * Gets the number of bytes in this window.
     */
    public fun length(): Int = rangeEnd - rangeStart

    /**
     * Changes the range of this window.
     */
    public fun set(start: Int, end: Int) {
        require(start in 0..inner.size) { "start out of bounds: $start" }
        require(end in 0..inner.size) { "end out of bounds: $end" }
        require(start <= end) { "start ($start) must not exceed end ($end)" }
        rangeStart = start
        rangeEnd = end
    }

    /**
     * Returns a copy of the active window bytes.
     */
    public fun asByteArray(): ByteArray =
        inner.copyOfRange(rangeStart, rangeEnd)

    /**
     * Consumes this window, returning the underlying buffer.
     */
    public fun intoInner(): ByteArray = inner
}
