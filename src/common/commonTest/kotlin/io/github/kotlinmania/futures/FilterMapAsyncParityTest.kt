package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals

class FilterMapAsyncParityTest {
    private sealed interface Step {
        data class Value(
            val value: Int?,
        ) : Step

        data object Pending : Step

        data object End : Step
    }

    private sealed interface TaskStep {
        data class Ready(
            val value: Int?,
        ) : TaskStep

        data object Pending : TaskStep

        data object Panic : TaskStep
    }

    private data class PanicResult(
        val message: String?,
    )

    private data class Snapshot(
        val outcome: Any,
        val sourcePolls: Int,
        val taskPolls: List<Int>,
        val inputs: List<Int?>,
        val hint: SizeHint,
        val terminated: Boolean,
    )

    private class Input(
        val events: List<Step>,
        val unknown: Boolean,
    ) : FusedStream<Int?> {
        var polls = 0

        override fun pollNext(context: TaskContext): Poll<Yield<Int?>> {
            val event = events.getOrNull(polls++) ?: Step.End
            return when (event) {
                is Step.Value -> Poll.Ready(Yield.Value(event.value))
                Step.Pending -> Poll.Pending
                Step.End -> Poll.Ready(Yield.End)
            }
        }

        override fun sizeHint(): SizeHint {
            if (unknown) return SizeHint(0, null)
            val remaining = events.drop(polls).count { it is Step.Value }
            return SizeHint(remaining, remaining)
        }

        override fun isTerminated(): Boolean = polls >= events.size
    }

    private fun check(events: List<Step>, plans: List<List<TaskStep>>, unknown: Boolean, expected: List<Snapshot>) {
        val source = Input(events, unknown)
        val inputs = mutableListOf<Int?>()
        val counts = mutableListOf<Int>()
        // Erased result permits the pre-repair signature to compile, exposing yielded Future objects as failures.
        val stream: FusedStream<*> =
            source.filterMap { item ->
                inputs.add(item)
                val index = counts.size
                counts.add(0)
                var position = 0
                Future<Int?> {
                    counts[index] += 1
                    when (val event = plans[index][position++]) {
                        is TaskStep.Ready -> Poll.Ready(event.value)
                        TaskStep.Pending -> Poll.Pending
                        TaskStep.Panic -> error("callback panic")
                    }
                }
            }
        val actual =
            expected.map {
                val outcome =
                    try {
                        stream.pollNext(TaskContext())
                    } catch (exception: IllegalStateException) {
                        PanicResult(exception.message)
                    }
                Snapshot(outcome, source.polls, counts.toList(), inputs.toList(), stream.sizeHint(), stream.isTerminated())
            }
        assertEquals(expected, actual)
    }

    @Test
    fun awaitWithoutReadingAhead() {
        check(listOf(Step.Value(1), Step.Value(2), Step.End), listOf(listOf(TaskStep.Pending, TaskStep.Ready(10)), listOf(TaskStep.Ready(20))), false, listOf(Snapshot(Poll.Pending, 1, listOf(1), listOf(1), SizeHint(0, 2), false), Snapshot(Poll.Ready(Yield.Value(10)), 1, listOf(2), listOf(1), SizeHint(0, 1), false), Snapshot(Poll.Ready(Yield.Value(20)), 2, listOf(2, 1), listOf(1, 2), SizeHint(0, 0), false), Snapshot(Poll.Ready(Yield.End), 3, listOf(2, 1), listOf(1, 2), SizeHint(0, 0), true)))
    }

    @Test
    fun readyNoneSkipsInSamePoll() {
        check(listOf(Step.Value(1), Step.Value(2), Step.End), listOf(listOf(TaskStep.Ready(null)), listOf(TaskStep.Ready(20))), false, listOf(Snapshot(Poll.Ready(Yield.Value(20)), 2, listOf(1, 1), listOf(1, 2), SizeHint(0, 0), false), Snapshot(Poll.Ready(Yield.End), 3, listOf(1, 1), listOf(1, 2), SizeHint(0, 0), true)))
    }

    @Test
    fun pendingThenNoneKeepsOrder() {
        check(listOf(Step.Value(1), Step.Value(2), Step.End), listOf(listOf(TaskStep.Pending, TaskStep.Ready(null)), listOf(TaskStep.Pending, TaskStep.Ready(20))), false, listOf(Snapshot(Poll.Pending, 1, listOf(1), listOf(1), SizeHint(0, 2), false), Snapshot(Poll.Pending, 2, listOf(2, 1), listOf(1, 2), SizeHint(0, 1), false), Snapshot(Poll.Ready(Yield.Value(20)), 2, listOf(2, 2), listOf(1, 2), SizeHint(0, 0), false), Snapshot(Poll.Ready(Yield.End), 3, listOf(2, 2), listOf(1, 2), SizeHint(0, 0), true)))
    }

    @Test
    fun sourcePendingDelaysCallback() {
        check(listOf(Step.Pending, Step.Value(7), Step.End), listOf(listOf(TaskStep.Ready(70))), false, listOf(Snapshot(Poll.Pending, 1, listOf(), listOf(), SizeHint(0, 1), false), Snapshot(Poll.Ready(Yield.Value(70)), 2, listOf(1), listOf(7), SizeHint(0, 0), false), Snapshot(Poll.Ready(Yield.End), 3, listOf(1), listOf(7), SizeHint(0, 0), true)))
    }

    @Test
    fun nullableInputsAndEmptyResult() {
        check(listOf(Step.Value(null), Step.Value(2), Step.End), listOf(listOf(TaskStep.Ready(null)), listOf(TaskStep.Ready(2))), false, listOf(Snapshot(Poll.Ready(Yield.Value(2)), 2, listOf(1, 1), listOf(null, 2), SizeHint(0, 0), false), Snapshot(Poll.Ready(Yield.End), 3, listOf(1, 1), listOf(null, 2), SizeHint(0, 0), true)))
    }

    @Test
    fun pendingLastItemPreventsTermination() {
        check(listOf(Step.Value(1)), listOf(listOf(TaskStep.Pending, TaskStep.Ready(10))), false, listOf(Snapshot(Poll.Pending, 1, listOf(1), listOf(1), SizeHint(0, 1), false), Snapshot(Poll.Ready(Yield.Value(10)), 1, listOf(2), listOf(1), SizeHint(0, 0), true)))
    }

    @Test
    fun unknownUpperRemainsUnknown() {
        check(listOf(Step.Value(1), Step.End), listOf(listOf(TaskStep.Pending, TaskStep.Ready(10))), true, listOf(Snapshot(Poll.Pending, 1, listOf(1), listOf(1), SizeHint(0, null), false), Snapshot(Poll.Ready(Yield.Value(10)), 1, listOf(2), listOf(1), SizeHint(0, null), false), Snapshot(Poll.Ready(Yield.End), 2, listOf(2), listOf(1), SizeHint(0, null), true)))
    }

    @Test
    fun emptySourceDoesNotCallTransform() {
        check(listOf(Step.End), listOf(), false, listOf(Snapshot(Poll.Ready(Yield.End), 1, listOf(), listOf(), SizeHint(0, 0), true)))
    }

    @Test
    fun futurePanicRetainsPending() {
        check(listOf(Step.Value(1), Step.End), listOf(listOf(TaskStep.Panic, TaskStep.Ready(10))), false, listOf(Snapshot(PanicResult("callback panic"), 1, listOf(1), listOf(1), SizeHint(0, 1), false), Snapshot(Poll.Ready(Yield.Value(10)), 1, listOf(2), listOf(1), SizeHint(0, 0), false), Snapshot(Poll.Ready(Yield.End), 2, listOf(2), listOf(1), SizeHint(0, 0), true)))
    }
}
