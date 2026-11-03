package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkipEofStateParityTest {
    private sealed interface Step {
        data class Value(
            val value: Int?,
        ) : Step

        data object Pending : Step

        data object End : Step
    }

    private data class Snapshot(
        val outcome: Poll<Yield<Int?>>,
        val polls: Int,
        val hint: SizeHint,
    )

    private class Input(
        val events: List<Step>,
        val unknown: Boolean = false,
    ) : Stream<Int?> {
        var polls = 0

        override fun pollNext(context: TaskContext): Poll<Yield<Int?>> =
            when (val event = events[polls++]) {
                is Step.Value -> Poll.Ready(Yield.Value(event.value))
                Step.Pending -> Poll.Pending
                Step.End -> Poll.Ready(Yield.End)
            }

        override fun sizeHint(): SizeHint {
            if (unknown) return SizeHint(0, null)
            val remaining = events.drop(polls).count { it is Step.Value }
            return SizeHint(remaining, remaining)
        }
    }

    private fun check(skip: Int, events: List<Step>, unknown: Boolean, initial: SizeHint, expected: List<Snapshot>) {
        val input = Input(events, unknown)
        val stream = input.skip(skip)
        assertEquals(initial, stream.sizeHint())
        val actual = expected.map { Snapshot(stream.pollNext(TaskContext()), input.polls, stream.sizeHint()) }
        assertEquals(expected, actual)
    }

    @Test
    fun zeroSkipPassThrough() {
        check(0, listOf(Step.Value(null), Step.Pending, Step.Value(2), Step.End), false, SizeHint(2, 2), listOf(Snapshot(Poll.Ready(Yield.Value(null)), 1, SizeHint(1, 1)), Snapshot(Poll.Pending, 2, SizeHint(1, 1)), Snapshot(Poll.Ready(Yield.Value(2)), 3, SizeHint(0, 0)), Snapshot(Poll.Ready(Yield.End), 4, SizeHint(0, 0))))
    }

    @Test
    fun exhaustedSkipPassThrough() {
        check(2, listOf(Step.Value(1), Step.Value(2), Step.Value(3), Step.Value(null), Step.End), false, SizeHint(2, 2), listOf(Snapshot(Poll.Ready(Yield.Value(3)), 3, SizeHint(1, 1)), Snapshot(Poll.Ready(Yield.Value(null)), 4, SizeHint(0, 0)), Snapshot(Poll.Ready(Yield.End), 5, SizeHint(0, 0))))
    }

    @Test
    fun finiteShortSource() {
        check(3, listOf(Step.Value(1), Step.End, Step.End), false, SizeHint(0, 0), listOf(Snapshot(Poll.Ready(Yield.End), 2, SizeHint(0, 0)), Snapshot(Poll.Ready(Yield.End), 3, SizeHint(0, 0))))
    }

    @Test
    fun countSurvivesEnd() {
        check(3, listOf(Step.Value(10), Step.End, Step.Value(20), Step.Value(30), Step.Value(40), Step.End), false, SizeHint(1, 1), listOf(Snapshot(Poll.Ready(Yield.End), 2, SizeHint(1, 1)), Snapshot(Poll.Ready(Yield.Value(40)), 5, SizeHint(0, 0)), Snapshot(Poll.Ready(Yield.End), 6, SizeHint(0, 0))))
    }

    @Test
    fun repeatedEndBeforeItems() {
        check(2, listOf(Step.End, Step.End, Step.Value(1), Step.Value(2), Step.Value(3), Step.End), false, SizeHint(1, 1), listOf(Snapshot(Poll.Ready(Yield.End), 1, SizeHint(1, 1)), Snapshot(Poll.Ready(Yield.End), 2, SizeHint(1, 1)), Snapshot(Poll.Ready(Yield.Value(3)), 5, SizeHint(0, 0)), Snapshot(Poll.Ready(Yield.End), 6, SizeHint(0, 0))))
    }

    @Test
    fun pendingDoesNotDecrement() {
        check(3, listOf(Step.Value(1), Step.End, Step.Pending, Step.Value(2), Step.Pending, Step.Value(3), Step.Value(4), Step.End), false, SizeHint(1, 1), listOf(Snapshot(Poll.Ready(Yield.End), 2, SizeHint(1, 1)), Snapshot(Poll.Pending, 3, SizeHint(1, 1)), Snapshot(Poll.Pending, 5, SizeHint(1, 1)), Snapshot(Poll.Ready(Yield.Value(4)), 7, SizeHint(0, 0)), Snapshot(Poll.Ready(Yield.End), 8, SizeHint(0, 0))))
    }

    @Test
    fun nullableItemsCountAsValues() {
        check(2, listOf(Step.Value(null), Step.End, Step.Value(null), Step.Value(null), Step.End), false, SizeHint(1, 1), listOf(Snapshot(Poll.Ready(Yield.End), 2, SizeHint(1, 1)), Snapshot(Poll.Ready(Yield.Value(null)), 4, SizeHint(0, 0)), Snapshot(Poll.Ready(Yield.End), 5, SizeHint(0, 0))))
    }

    @Test
    fun unknownHintKeepsCounter() {
        check(2, listOf(Step.Value(1), Step.End, Step.Value(2), Step.Value(3), Step.End), true, SizeHint(0, null), listOf(Snapshot(Poll.Ready(Yield.End), 2, SizeHint(0, null)), Snapshot(Poll.Ready(Yield.Value(3)), 4, SizeHint(0, null)), Snapshot(Poll.Ready(Yield.End), 5, SizeHint(0, null))))
    }

    @Test
    fun canonicalFuseStopsAfterEnd() {
        val input = Input(listOf(Step.Value(1), Step.End, Step.Value(2), Step.Value(3)))
        val stream = input.skip(3).fuse()
        assertEquals(false, stream.isTerminated())
        assertEquals(Poll.Ready(Yield.End), stream.pollNext(TaskContext()))
        assertTrue(stream.isTerminated())
        assertEquals(SizeHint(0, 0), stream.sizeHint())
        assertEquals(Poll.Ready(Yield.End), stream.pollNext(TaskContext()))
        assertEquals(2, input.polls)
    }
}
