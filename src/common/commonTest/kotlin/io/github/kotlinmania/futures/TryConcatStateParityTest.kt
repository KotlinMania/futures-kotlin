package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TryConcatStateParityTest {
    private sealed interface Step {
        data class Values(
            val items: List<Int?>,
        ) : Step

        data class Error(
            val error: String?,
        ) : Step

        data object Pending : Step

        data object End : Step

        data object Panic : Step
    }

    private sealed interface Outcome {
        data object Pending : Outcome

        data class Ok(
            val items: List<Int?>,
        ) : Outcome

        data class Err(
            val error: String?,
        ) : Outcome

        data class Panic(
            val message: String?,
        ) : Outcome
    }

    private fun check(events: List<Step>, expected: List<Outcome>, expectedPolls: Int) {
        var polls = 0
        val input =
            object : TryStream<List<Int?>, String?> {
                override fun pollNext(context: TaskContext): Poll<Yield<Try<List<Int?>, String?>>> =
                    when (val event = events[polls++]) {
                        is Step.Values -> Poll.Ready(Yield.Value(Try.Ok(event.items)))
                        is Step.Error -> Poll.Ready(Yield.Value(Try.Err(event.error)))
                        Step.Pending -> Poll.Pending
                        Step.End -> Poll.Ready(Yield.End)
                        Step.Panic -> error("stream panic")
                    }
            }
        val future = input.tryConcat()
        val actual =
            expected.map {
                try {
                    when (val polled = future.poll(TaskContext())) {
                        Poll.Pending -> {
                            Outcome.Pending
                        }

                        is Poll.Ready -> {
                            when (val result = polled.value) {
                                is Try.Ok -> Outcome.Ok(result.value)
                                is Try.Err -> Outcome.Err(result.error)
                            }
                        }
                    }
                } catch (exception: IllegalStateException) {
                    Outcome.Panic(exception.message)
                }
            }
        assertEquals(expected, actual)
        assertEquals(expectedPolls, polls)
    }

    @Test
    fun emptyAndRepeat() {
        check(listOf(Step.End, Step.End), listOf(Outcome.Ok(listOf()), Outcome.Ok(listOf())), 2)
    }

    @Test
    fun completedAccumulatorTaken() {
        check(listOf(Step.Values(listOf(1, 2)), Step.Values(listOf(3)), Step.End, Step.End), listOf(Outcome.Ok(listOf(1, 2, 3)), Outcome.Ok(listOf())), 4)
    }

    @Test
    fun errorRetainsAccumulation() {
        check(listOf(Step.Values(listOf(1)), Step.Error("fail"), Step.Values(listOf(2)), Step.End, Step.End), listOf(Outcome.Err("fail"), Outcome.Ok(listOf(1, 2)), Outcome.Ok(listOf())), 5)
    }

    @Test
    fun pendingPreservesAccumulation() {
        check(listOf(Step.Values(listOf(1)), Step.Pending, Step.Values(listOf(2)), Step.End, Step.End), listOf(Outcome.Pending, Outcome.Ok(listOf(1, 2)), Outcome.Ok(listOf())), 5)
    }

    @Test
    fun nullableItemsAndError() {
        check(listOf(Step.Values(listOf(null)), Step.Error(null), Step.Values(listOf(1)), Step.End, Step.End), listOf(Outcome.Err(null), Outcome.Ok(listOf(null, 1)), Outcome.Ok(listOf())), 5)
    }

    @Test
    fun panicRetainsAccumulation() {
        check(listOf(Step.Values(listOf(1)), Step.Panic, Step.Values(listOf(2)), Step.End, Step.End), listOf(Outcome.Panic("stream panic"), Outcome.Ok(listOf(1, 2)), Outcome.Ok(listOf())), 5)
    }

    @Test
    fun canonicalFuse() {
        val future =
            streamIter(listOf<Try<List<Int>, String?>>(Try.Ok(listOf(1))))
                .asTryStream()
                .tryConcat()
                .fuse()
        assertFalse(future.isTerminated())
        assertEquals(Poll.Ready(Try.Ok(listOf(1))), future.poll(TaskContext()))
        assertTrue(future.isTerminated())
        assertEquals(Poll.Pending, future.poll(TaskContext()))
    }

    @Test
    fun rawAdaptorHasNoFusedPromise() {
        val future: Any =
            streamIter(emptyList<Try<List<Int>, String?>>())
                .asTryStream()
                .tryConcat()
        assertFalse(future is FusedFuture<*>)
    }
}
