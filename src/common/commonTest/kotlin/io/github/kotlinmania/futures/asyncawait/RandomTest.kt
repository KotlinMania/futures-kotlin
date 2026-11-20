// port-lint: tests async_await/random.rs
package io.github.kotlinmania.futures.asyncawait

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RandomTest {
    @Test
    fun shufflePreservesElementsAndPermutes() {
        val empty = mutableListOf<Int>()
        shuffle(empty)
        assertEquals(emptyList(), empty)

        val single = mutableListOf(42)
        shuffle(single)
        assertEquals(listOf(42), single)

        val baseline = (0 until 16).toList()
        val items = baseline.toMutableList()
        var sawDifferentOrder = false
        repeat(16) {
            shuffle(items)
            assertEquals(baseline, items.sorted())
            if (items != baseline) {
                sawDifferentOrder = true
            }
        }
        assertTrue(sawDifferentOrder)
    }
}
