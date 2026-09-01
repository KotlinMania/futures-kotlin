// port-lint: tests stream_try_stream.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlattenUnorderedTest {
    @Test
    fun testFlattenUnorderedBasic() {
        val s1 = streamIter(listOf(1, 2))
        val s2 = streamIter(listOf(3, 4))
        val outer = streamIter(listOf(s1, s2))
        val flattened = outer.flattenUnordered()
        val context = TaskContext()

        val results = mutableListOf<Int>()
        while (true) {
            when (val p = flattened.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> results.add(y.value)
                        Yield.End -> break
                    }
                }
                Poll.Pending -> {}
            }
        }
        assertEquals(listOf(1, 2, 3, 4), results)
    }

    @Test
    fun testTryFlattenUnordered() {
        val s1 = streamIter(listOf(Try.ok(10), Try.ok(20))).asTryStream()
        val s2 = streamIter(listOf(Try.ok(30))).asTryStream()
        val outer = streamIter(listOf(Try.ok(s1), Try.ok(s2))).asTryStream()
        val flattened = outer.tryFlattenUnordered()
        val context = TaskContext()

        val results = mutableListOf<Int>()
        while (true) {
            when (val p = flattened.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            when (val item = y.value) {
                                is Try.Ok -> results.add(item.value)
                                is Try.Err -> {}
                            }
                        }
                        Yield.End -> break
                    }
                }
                Poll.Pending -> {}
            }
        }
        assertEquals(listOf(10, 20, 30), results)
    }

    @Test
    fun testTryFlattenUnorderedWithError() {
        val s1 = streamIter(listOf(Try.ok(10), Try.err("inner error"))).asTryStream()
        val outer = streamIter(listOf(Try.ok(s1))).asTryStream()
        val flattened = outer.tryFlattenUnordered()
        val context = TaskContext()

        val p1 = flattened.pollNext(context)
        assertTrue(p1 is Poll.Ready)
        assertEquals(Yield.Value(Try.ok(10)), p1.value)

        val p2 = flattened.pollNext(context)
        assertTrue(p2 is Poll.Ready)
        assertEquals(Yield.Value(Try.err("inner error")), p2.value)
    }
}
