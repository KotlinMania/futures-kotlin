// port-lint: tests futures-util/src/stream/unfold.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamUnfoldTest {
    @Test
    fun testUnfoldStream() {
        val st =
            streamUnfold(0) { state ->
                if (state <= 2) {
                    val nextState = state + 1
                    val yielded = state * 2
                    ready(Pair(yielded, nextState) as Pair<Int, Int>?)
                } else {
                    ready(null as Pair<Int, Int>?)
                }
            }
        val cx = TaskContext()

        val p1 = st.pollNext(cx)
        assertTrue(p1 is Poll.Ready)
        assertEquals(Yield.value(0), p1.value)

        val p2 = st.pollNext(cx)
        assertTrue(p2 is Poll.Ready)
        assertEquals(Yield.value(2), p2.value)

        val p3 = st.pollNext(cx)
        assertTrue(p3 is Poll.Ready)
        assertEquals(Yield.value(4), p3.value)

        val p4 = st.pollNext(cx)
        assertTrue(p4 is Poll.Ready)
        assertEquals(Yield.end(), p4.value)
        assertTrue(st.isTerminated())
    }

    @Test
    fun unfold1() {
        val stream = streamUnfold(0) { state ->
            if (state <= 2) {
                ready(Pair(state * 2, state + 1) as Pair<Int, Int>?)
            } else {
                ready(null as Pair<Int, Int>?)
            }
        }
        val cx = TaskContext()
        assertEquals(Poll.ready(Yield.value(0)), stream.pollNext(cx))
        assertEquals(Poll.ready(Yield.value(2)), stream.pollNext(cx))
        assertEquals(Poll.ready(Yield.value(4)), stream.pollNext(cx))
        assertEquals(Poll.ready(Yield.end()), stream.pollNext(cx))
        assertTrue(stream.isTerminated())
    }
}
