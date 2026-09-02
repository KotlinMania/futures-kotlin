// port-lint: tests futures/tests/io_window.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.Window
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WindowTest {
    @Test
    fun testWindowSlicing() {
        val bytes = byteArrayOf(10, 20, 30, 40, 50)
        val window = Window(bytes)

        assertEquals(0, window.start())
        assertEquals(5, window.end())
        assertEquals(5, window.length())
        assertContentEquals(byteArrayOf(10, 20, 30, 40, 50), window.asByteArray())

        window.set(1, 4)
        assertEquals(1, window.start())
        assertEquals(4, window.end())
        assertEquals(3, window.length())
        assertContentEquals(byteArrayOf(20, 30, 40), window.asByteArray())

        val inner = window.intoInner()
        assertContentEquals(bytes, inner)
    }

    @Test
    fun testWindowBoundsValidation() {
        val bytes = byteArrayOf(1, 2, 3)
        val window = Window(bytes)

        assertFailsWith<IllegalArgumentException> {
            window.set(-1, 2)
        }
        assertFailsWith<IllegalArgumentException> {
            window.set(0, 5)
        }
        assertFailsWith<IllegalArgumentException> {
            window.set(3, 1)
        }
    }

    @Test
    fun set() {
        val buffer = Window(byteArrayOf(1, 2, 3))
        buffer.set(0, 3)
        assertContentEquals(byteArrayOf(1, 2, 3), buffer.asByteArray())
        buffer.set(3, 3)
        assertContentEquals(byteArrayOf(), buffer.asByteArray())
        buffer.set(0, 2)
        assertContentEquals(byteArrayOf(1, 2), buffer.asByteArray())
    }

    @Test
    fun setPanicOutOfBounds() {
        val buffer = Window(byteArrayOf(1, 2, 3))
        assertFailsWith<IllegalArgumentException> {
            buffer.set(2, 4)
        }
    }

    @Test
    fun setPanicStartIsGreaterThanEnd() {
        val buffer = Window(byteArrayOf(1, 2, 3))
        assertFailsWith<IllegalArgumentException> {
            buffer.set(3, 2)
        }
    }
}
