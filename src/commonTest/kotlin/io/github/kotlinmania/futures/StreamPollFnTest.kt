// port-lint: tests futures-util/src/stream/poll_fn.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamPollFnTest {
    @Test
    fun testPollFnStream() {
        var counter = 2
        val st =
            streamPollFn<String> {
                if (counter == 0) {
                    Poll.ready(Yield.end())
                } else {
                    counter -= 1
                    Poll.ready(Yield.value("item $counter"))
                }
            }
        val cx = TaskContext()

        val p1 = st.pollNext(cx)
        assertTrue(p1 is Poll.Ready)
        assertEquals(Yield.value("item 1"), p1.value)

        val p2 = st.pollNext(cx)
        assertTrue(p2 is Poll.Ready)
        assertEquals(Yield.value("item 0"), p2.value)

        val p3 = st.pollNext(cx)
        assertTrue(p3 is Poll.Ready)
        assertEquals(Yield.end(), p3.value)
    }
}
