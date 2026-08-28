// port-lint: tests futures-util/src/stream/select_with_strategy.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamSelectWithStrategyTest {
    @Test
    fun testPollNextToggleAndOther() {
        assertEquals(PollNext.Right, PollNext.Left.other())
        assertEquals(PollNext.Left, PollNext.Right.other())
        assertEquals(PollNext.Right, PollNext.Left.toggle())
        assertEquals(PollNext.Left, PollNext.default())
    }

    @Test
    fun testPriorityLeftStrategy() {
        val left = streamIter(listOf(1, 2, 3))
        val right = streamIter(listOf(10, 20, 30))

        val selected = selectWithStrategy(left, right) { PollNext.Left }
        val cx = TaskContext()

        val results = mutableListOf<Int>()
        while (!selected.isTerminated()) {
            when (val p = selected.pollNext(cx)) {
                is Poll.Ready ->
                    when (val y = p.value) {
                        is Yield.Value -> results.add(y.value)
                        Yield.End -> break
                    }
                Poll.Pending -> {}
            }
        }
        assertEquals(listOf(1, 2, 3, 10, 20, 30), results)
    }

    @Test
    fun testRoundRobinStrategy() {
        val left = streamIter(listOf(1, 2, 3))
        val right = streamIter(listOf(10, 20, 30))

        var last = PollNext.Right
        val selected =
            selectWithStrategy(left, right, last) {
                last = last.other()
                last
            }
        val cx = TaskContext()

        val results = mutableListOf<Int>()
        while (!selected.isTerminated()) {
            when (val p = selected.pollNext(cx)) {
                is Poll.Ready ->
                    when (val y = p.value) {
                        is Yield.Value -> results.add(y.value)
                        Yield.End -> break
                    }
                Poll.Pending -> {}
            }
        }
        assertEquals(listOf(1, 10, 2, 20, 3, 30), results)
    }

    @Test
    fun testSelectWithStrategyDoesntTerminateEarly() {
        for (side in listOf(PollNext.Left, PollNext.Right)) {
            val timesShouldPoll = 10
            var count = 0
            val slowStream =
                object : Stream<Int> {
                    override fun pollNext(context: TaskContext): Poll<Yield<Int>> {
                        count++
                        if (count % 2 == 0) {
                            return Poll.Pending
                        }
                        if (count >= timesShouldPoll) {
                            return Poll.Ready(Yield.End)
                        }
                        return Poll.Ready(Yield.Value(count))
                    }
                }
            val b = streamIter(listOf(10, 20))
            val selected = selectWithStrategy(slowStream, b) { side }
            val cx = TaskContext()

            while (!selected.isTerminated()) {
                val p = selected.pollNext(cx)
                if (p is Poll.Ready && p.value is Yield.End) break
            }
            assertEquals(timesShouldPoll + 1, count)
        }
    }
}
