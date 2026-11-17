package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConcatStateParityTest {
    private sealed interface Step {
        data class Values(
            val items: List<Int?>,
        ) : Step

        data object Pending : Step

        data object InactivePending : Step

        data object End : Step

        data object Panic : Step
    }

    private class Input(
        val events: List<Step>,
    ) : FusedStream<List<Int?>> {
        var terminated = false
        var polls = 0

        override fun isTerminated(): Boolean = terminated

        override fun pollNext(context: TaskContext): Poll<Yield<List<Int?>>> =
            when (val event = events.getOrNull(polls++) ?: Step.End) {
                is Step.Values -> {
                    Poll.Ready(Yield.Value(event.items))
                }

                Step.Pending -> {
                    Poll.Pending
                }

                Step.InactivePending -> {
                    terminated = true
                    Poll.Pending
                }

                Step.End -> {
                    terminated = true
                    Poll.Ready(Yield.End)
                }

                Step.Panic -> {
                    error("source panic")
                }
            }
    }

    private sealed interface Outcome {
        data class Ready(
            val items: List<Int?>,
        ) : Outcome

        data object Pending : Outcome

        data class Panic(
            val message: String?,
        ) : Outcome
    }

    private data class Snapshot(
        val outcome: Outcome,
        val polls: Int,
        val terminated: Boolean,
    )

    private fun check(events: List<Step>, expected: List<Snapshot>) {
        val input = Input(events)
        val future = input.concat()
        assertFalse(future.isTerminated())
        val actual =
            expected.map {
                val outcome =
                    try {
                        when (val polled = future.poll(TaskContext())) {
                            is Poll.Ready -> Outcome.Ready(polled.value)
                            Poll.Pending -> Outcome.Pending
                        }
                    } catch (exception: IllegalStateException) {
                        Outcome.Panic(exception.message)
                    }
                Snapshot(outcome, input.polls, future.isTerminated())
            }
        assertEquals(expected, actual)
    }

    @Test
    fun emptySourceBeforePoll() {
        val future = emptyStream<List<Int?>>().concat()
        assertTrue(future.isTerminated())
        assertEquals(Poll.Ready(emptyList()), future.poll(TaskContext()))
        assertTrue(future.isTerminated())
    }

    @Test
    fun inactivePendingSourceWithoutAccumulator() {
        val future = pendingStream<List<Int?>>().concat()
        assertTrue(future.isTerminated())
        assertEquals(Poll.Pending, future.poll(TaskContext()))
        assertTrue(future.isTerminated())
    }

    @Test
    fun pendingPreservesNullableChunks() {
        check(listOf(Step.Values(listOf(1, null)), Step.Pending, Step.Values(listOf(2)), Step.End), listOf(Snapshot(Outcome.Pending, 2, false), Snapshot(Outcome.Ready(listOf(1, null, 2)), 4, true)))
    }

    @Test
    fun someEmptyAccumulatorIsNotNone() {
        check(listOf(Step.Values(emptyList()), Step.InactivePending), listOf(Snapshot(Outcome.Pending, 2, false)))
    }

    @Test
    fun completedAccumulatorIsTaken() {
        check(listOf(Step.Values(listOf(1, null)), Step.End, Step.End), listOf(Snapshot(Outcome.Ready(listOf(1, null)), 2, true), Snapshot(Outcome.Ready(emptyList()), 3, true)))
    }

    @Test
    fun sourcePanicRetainsAccumulator() {
        check(listOf(Step.Values(listOf(1)), Step.Panic, Step.Values(listOf(2)), Step.End, Step.End), listOf(Snapshot(Outcome.Panic("source panic"), 2, false), Snapshot(Outcome.Ready(listOf(1, 2)), 4, true), Snapshot(Outcome.Ready(emptyList()), 5, true)))
    }
}
