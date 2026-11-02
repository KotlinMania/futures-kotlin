package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TryFoldStateParityTest {
    private fun result(poll: Poll<Try<Int?, String?>>): Try<Int?, String?> = assertIs<Poll.Ready<Try<Int?, String?>>>(poll).value

    @Test
    fun emptyStreamHasAPresentNullAccumulatorUntilCompletion() {
        val fold = streamIter(emptyList<Try<Int, String?>>()).asTryStream().tryFold<Int, String?, Int?>(null) { _, _ -> error("not called") }
        assertFalse(fold.isTerminated())
        assertNull(assertIs<Try.Ok<Int?>>(result(fold.poll(TaskContext()))).value)
        assertTrue(fold.isTerminated())
        assertEquals("Fold polled after completion", assertFailsWith<RuntimeException> { fold.poll(TaskContext()) }.message)
    }

    @Test
    fun nullFutureSuccessIsPassedToNextOperationAndNullableFutureErrorTerminates() {
        val seen = mutableListOf<Int?>()
        val fold =
            streamIter(listOf<Try<Int, String?>>(Try.Ok(1), Try.Ok(2))).asTryStream().tryFold<Int, String?, Int?>(0) { accum, item ->
                seen.add(accum)
                ready(if (item == 1) Try.Ok(null) else Try.Err(null))
            }
        assertNull(assertIs<Try.Err<String?>>(result(fold.poll(TaskContext()))).error)
        assertEquals(listOf<Int?>(0, null), seen)
        assertTrue(fold.isTerminated())
        assertFailsWith<RuntimeException> { fold.poll(TaskContext()) }
    }

    @Test
    fun nullableAccumulatorSurvivesInputPendingAndOperationPending() {
        var inputPolls = 0
        val input =
            object : TryStream<Int, String?> {
                override fun pollNext(context: TaskContext): Poll<Yield<Try<Int, String?>>> {
                    inputPolls += 1
                    return when (inputPolls) {
                        1 -> {
                            context.wakeByRef()
                            Poll.Pending
                        }

                        2 -> {
                            Poll.Ready(Yield.Value(Try.Ok(1)))
                        }

                        else -> {
                            Poll.Ready(Yield.End)
                        }
                    }
                }
            }
        var released = false
        var wake: Waker? = null
        var actionPolls = 0
        val action =
            object : Future<Try<Int?, String?>> {
                override fun poll(context: TaskContext): Poll<Try<Int?, String?>> {
                    actionPolls += 1
                    if (!released) {
                        wake = context.waker
                        return Poll.Pending
                    }
                    return Poll.Ready(Try.Ok(7))
                }
            }
        var calls = 0
        val fold =
            input.tryFold<Int, String?, Int?>(null) { accum, _ ->
                assertNull(accum)
                calls += 1
                action
            }
        assertSame(Poll.Pending, fold.poll(TaskContext()))
        assertEquals(1, inputPolls)
        assertEquals(0, calls)
        assertFalse(fold.isTerminated())
        assertSame(Poll.Pending, fold.poll(TaskContext()))
        assertEquals(1, calls)
        assertEquals(1, actionPolls)
        assertEquals(2, inputPolls)
        released = true
        wake!!.wakeByRef()
        assertEquals(7, assertIs<Try.Ok<Int?>>(result(fold.poll(TaskContext()))).value)
        assertEquals(1, calls)
        assertEquals(2, actionPolls)
        assertTrue(fold.isTerminated())
    }

    @Test
    fun nullableInputErrorTerminatesWithoutInvokingOperation() {
        var calls = 0
        val fold =
            streamIter(listOf<Try<Int, String?>>(Try.Err(null))).asTryStream().tryFold<Int, String?, Int?>(3) { _, _ ->
                calls += 1
                ready(Try.Ok(99))
            }
        assertNull(assertIs<Try.Err<String?>>(result(fold.poll(TaskContext()))).error)
        assertEquals(0, calls)
        assertTrue(fold.isTerminated())
        assertEquals("Fold polled after completion", assertFailsWith<RuntimeException> { fold.poll(TaskContext()) }.message)
    }

    @Test
    fun operationCreationFailureConsumesAccumulatorAndTerminates() {
        val failure = IllegalStateException("operation")
        var calls = 0
        val fold =
            streamIter(listOf<Try<Int, String?>>(Try.Ok(1), Try.Ok(2))).asTryStream().tryFold<Int, String?, Int?>(3) { _, _ ->
                calls += 1
                throw failure
            }
        assertSame(failure, assertFailsWith<IllegalStateException> { fold.poll(TaskContext()) })
        assertTrue(fold.isTerminated())
        assertEquals("Fold polled after completion", assertFailsWith<RuntimeException> { fold.poll(TaskContext()) }.message)
        assertEquals(1, calls)
    }

    @Test
    fun operationFutureFailureKeepsPendingFutureState() {
        val failure = IllegalStateException("operation future")
        val action =
            object : Future<Try<Int?, String?>> {
                override fun poll(context: TaskContext): Poll<Try<Int?, String?>> = throw failure
            }
        val fold = streamIter(listOf<Try<Int, String?>>(Try.Ok(1))).asTryStream().tryFold<Int, String?, Int?>(3) { _, _ -> action }
        assertSame(failure, assertFailsWith<IllegalStateException> { fold.poll(TaskContext()) })
        assertFalse(fold.isTerminated())
    }
}
