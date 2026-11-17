package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CollectHandoffParityTest {
    private class Input<T>(
        events: List<Poll<Yield<T>>>,
    ) : Stream<T> {
        private val events = events.toMutableList()
        var polls = 0
            private set

        override fun pollNext(context: TaskContext): Poll<Yield<T>> {
            polls += 1
            val event = if (events.isEmpty()) Poll.Ready(Yield.End) else events.removeAt(0)
            if (event is Poll.Pending) context.wakeByRef()
            return event
        }
    }

    @Test
    fun collectTransfersItsBufferAndRepeatedEndReturnsEmpty() {
        val input = Input<Int?>(listOf(Poll.Ready(Yield.Value(null)), Poll.Ready(Yield.Value(1)), Poll.Ready(Yield.End)))
        val future = input.collect()
        val first = assertIs<Poll.Ready<List<Int?>>>(future.poll(TaskContext())).value
        assertEquals(listOf(null, 1), first)
        assertEquals(Poll.Ready(emptyList()), future.poll(TaskContext()))
        assertEquals(listOf(null, 1), first)
        assertEquals(4, input.polls)
    }

    @Test
    fun collectDelegatesTerminationToAlreadyTerminatedInput() {
        val input =
            object : FusedStream<Int> {
                override fun isTerminated(): Boolean = true

                override fun pollNext(context: TaskContext): Poll<Yield<Int>> = Poll.Ready(Yield.End)
            }
        val future = input.collect()
        assertTrue(future.isTerminated())
        assertEquals(Poll.Ready(emptyList()), future.poll(TaskContext()))
        assertTrue(future.isTerminated())
    }

    @Test
    fun nonFusedInputResumingAfterEndStartsWithANewCollection() {
        val input =
            Input(
                listOf(
                    Poll.Ready(Yield.Value(1)),
                    Poll.Ready(Yield.End),
                    Poll.Pending,
                    Poll.Ready(Yield.Value(2)),
                    Poll.Ready(Yield.End),
                ),
            )
        val future = input.collect()
        val first = assertIs<Poll.Ready<List<Int>>>(future.poll(TaskContext())).value
        assertEquals(listOf(1), first)
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(Poll.Ready(listOf(2)), future.poll(TaskContext()))
        assertEquals(listOf(1), first)
        assertEquals(5, input.polls)
    }

    @Test
    fun tryCollectTransfersSuccessfulBufferAndRepeatedEndReturnsEmpty() {
        val input =
            Input<Try<Int?, String>>(
                listOf(Poll.Ready(Yield.Value(Try.Ok(null))), Poll.Ready(Yield.Value(Try.Ok(1))), Poll.Ready(Yield.End)),
            )
        val future = input.asTryStream().tryCollect()
        val first = assertIs<Try.Ok<List<Int?>>>(assertIs<Poll.Ready<Try<List<Int?>, String>>>(future.poll(TaskContext())).value).value
        assertEquals(listOf(null, 1), first)
        assertEquals(Poll.Ready(Try.Ok(emptyList())), future.poll(TaskContext()))
        assertEquals(listOf(null, 1), first)
        assertEquals(4, input.polls)
    }

    @Test
    fun tryCollectRetainsPrefixAcrossPendingAndErrorThenTransfersOnSuccess() {
        val input =
            Input<Try<Int?, String>>(
                listOf(
                    Poll.Ready(Yield.Value(Try.Ok(null))),
                    Poll.Pending,
                    Poll.Ready(Yield.Value(Try.Ok(1))),
                    Poll.Ready(Yield.Value(Try.Err("input"))),
                    Poll.Ready(Yield.Value(Try.Ok(2))),
                    Poll.Ready(Yield.End),
                ),
            )
        val future = input.asTryStream().tryCollect()
        assertSame(Poll.Pending, future.poll(TaskContext()))
        assertEquals(Poll.Ready(Try.Err("input")), future.poll(TaskContext()))
        val first = assertIs<Try.Ok<List<Int?>>>(assertIs<Poll.Ready<Try<List<Int?>, String>>>(future.poll(TaskContext())).value).value
        assertEquals(listOf(null, 1, 2), first)
        assertEquals(Poll.Ready(Try.Ok(emptyList())), future.poll(TaskContext()))
        assertEquals(listOf(null, 1, 2), first)
        assertEquals(7, input.polls)
    }

    @Test
    fun tryCollectKeepsDelegatingTerminationToItsInput() {
        val input =
            object :
                TryStream<Int?, String>,
                FusedStream<Try<Int?, String>> {
                override fun isTerminated(): Boolean = true

                override fun pollNext(context: TaskContext): Poll<Yield<Try<Int?, String>>> = Poll.Ready(Yield.End)
            }
        val future = input.tryCollect()
        assertTrue(future.isTerminated())
        assertEquals(Poll.Ready(Try.Ok(emptyList())), future.poll(TaskContext()))
        assertTrue(future.isTerminated())
    }
}
