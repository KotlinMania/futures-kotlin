// port-lint: tests futures/tests/stream.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamTest {
    private class ListStream(
        private val items: List<Int>,
    ) : Stream<Int> {
        private var index = 0

        override fun pollNext(context: TaskContext): Poll<Yield<Int>> =
            if (index < items.size) {
                Poll.ready(Yield.value(items[index++]))
            } else {
                Poll.ready(Yield.end())
            }

        override fun sizeHint(): SizeHint {
            val remaining = items.size - index
            return SizeHint(lower = remaining, upper = remaining)
        }
    }

    @Test
    fun yieldsValuesUntilEnd() {
        val stream = ListStream(listOf(1, 2, 3))
        val context = TaskContext()

        assertEquals(Poll.Ready(Yield.Value(1)), stream.pollNext(context))
        assertEquals(Poll.Ready(Yield.Value(2)), stream.pollNext(context))
        assertEquals(Poll.Ready(Yield.Value(3)), stream.pollNext(context))
        assertEquals(Poll.Ready(Yield.End), stream.pollNext(context))
    }

    @Test
    fun pendingStreamWakesContext() {
        var woke = false
        val context = TaskContext(Waker { woke = true })
        val stream =
            object : Stream<Int> {
                override fun pollNext(context: TaskContext): Poll<Yield<Int>> {
                    context.wakeByRef()
                    return Poll.pending()
                }
            }

        assertEquals(Poll.Pending, stream.pollNext(context))
        assertTrue(woke)
    }

    @Test
    fun defaultSizeHintIsZeroAndUnbounded() {
        val stream =
            object : Stream<Int> {
                override fun pollNext(context: TaskContext): Poll<Yield<Int>> = Poll.ready(Yield.end())
            }

        assertEquals(SizeHint(lower = 0, upper = null), stream.sizeHint())
    }

    @Test
    fun isTerminatedReflectsDoneState() {
        class OnceStream : FusedStream<Int> {
            private var yielded = false
            private var terminated = false

            override fun isTerminated(): Boolean = terminated

            override fun pollNext(context: TaskContext): Poll<Yield<Int>> =
                if (!yielded) {
                    yielded = true
                    Poll.ready(Yield.value(99))
                } else {
                    terminated = true
                    Poll.ready(Yield.end())
                }
        }

        val stream = OnceStream()
        assertFalse(stream.isTerminated())
        assertEquals(Poll.Ready(Yield.Value(99)), stream.pollNext(TaskContext()))
        assertFalse(stream.isTerminated())
        assertEquals(Poll.Ready(Yield.End), stream.pollNext(TaskContext()))
        assertTrue(stream.isTerminated())
    }

    @Test
    fun tryStreamViewDelegatesToUnderlyingStream() {
        val items = listOf<Try<Int, String>>(Try.Ok(1), Try.Err("boom"))
        var idx = 0
        val source: Stream<Try<Int, String>> =
            object : Stream<Try<Int, String>> {
                override fun pollNext(context: TaskContext): Poll<Yield<Try<Int, String>>> =
                    if (idx < items.size) {
                        Poll.ready(Yield.value(items[idx++]))
                    } else {
                        Poll.ready(Yield.end())
                    }
            }

        val tryStream = source.asTryStream()
        assertEquals(
            Poll.Ready(Yield.Value(Try.Ok(1))),
            tryStream.tryPollNext(TaskContext()),
        )
        assertEquals(
            Poll.Ready(Yield.Value(Try.Err("boom"))),
            tryStream.tryPollNext(TaskContext()),
        )
        assertEquals(Poll.Ready(Yield.End), tryStream.tryPollNext(TaskContext()))
    }

    @Test
    fun yieldFoldHandlesBothBranches() {
        val v: Yield<Int> = Yield.Value(7)
        val e: Yield<Int> = Yield.End

        assertEquals("got 7", v.fold(onValue = { "got $it" }, onEnd = { "done" }))
        assertEquals("done", e.fold(onValue = { "got $it" }, onEnd = { "done" }))
    }

    @Test
    fun valueOrNullReturnsValueOrNull() {
        val v: Yield<Int> = Yield.Value(42)
        val e: Yield<Int> = Yield.End

        assertEquals(42, v.valueOrNull())
        assertNull(e.valueOrNull())
        assertNotNull(v.valueOrNull())
    }

    @Test
    fun select() {
        fun selectAndCompare(a: List<Int>, b: List<Int>, expected: List<Int>) {
            val stA = streamIter(a)
            val stB = streamIter(b)
            val selected = streamSelect(stA, stB)
            val cx = TaskContext()
            val result = mutableListOf<Int>()
            while (true) {
                when (val p = selected.pollNext(cx)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> result.add(y.value)
                            Yield.End -> break
                        }
                    }
                    Poll.Pending -> break
                }
            }
            assertEquals(expected, result)
        }

        selectAndCompare(listOf(1, 2, 3), listOf(4, 5, 6), listOf(1, 4, 2, 5, 3, 6))
        selectAndCompare(listOf(1, 2, 3), listOf(4, 5), listOf(1, 4, 2, 5, 3))
        selectAndCompare(listOf(1, 2), listOf(4, 5, 6), listOf(1, 4, 2, 5, 6))
    }

    @Test
    fun flatMap() {
        val st = streamIter(listOf(
            streamIter(listOf(0, 1, 2, 3, 4)),
            streamIter(listOf(6, 7, 8, 9, 10)),
            streamIter(listOf(0, 1, 2)),
        ))

        val flat = st.flatMap { s -> s.filter { v -> v % 2 == 0 } }
        val cx = TaskContext()
        val collected = flat.collect().poll(cx)
        assertEquals(Poll.Ready(listOf(0, 2, 4, 6, 8, 10, 0, 2)), collected)
    }

    @Test
    fun scan() {
        val values = streamIter(listOf(1, 2, 3, 4, 6, 8, 2))
            .scan(1) { state, e ->
                val nextState = state + 1
                if (e < nextState) {
                    Pair(nextState, e)
                } else {
                    null
                }
            }

        val cx = TaskContext()
        val collected = values.collect().poll(cx)
        assertEquals(Poll.Ready(listOf(1, 2, 3, 4)), collected)
    }

    @Test
    fun takeUntil() {
        fun makeStopFut(stopOn: Int): Future<Unit> {
            var i = 0
            return pollFn {
                i += 1
                if (i <= stopOn) {
                    Poll.pending()
                } else {
                    Poll.ready(Unit)
                }
            }
        }

        val cx = TaskContext()

        // Verify stopping works
        val stream1 = streamIter((1..10).toList())
        val stopFut1 = makeStopFut(5)
        val takeUntil1 = stream1.takeUntil(stopFut1)
        val folded1 = takeUntil1.fold(0) { _, i -> i }
        assertEquals(Poll.Ready(5), folded1.poll(cx))

        // Verify takeFuture() works
        val stream2 = streamIter((1..10).toList())
        val stopFut2 = makeStopFut(5)
        val takeUntil2 = stream2.takeUntil(stopFut2)
        assertEquals(Poll.Ready(Yield.Value(1)), takeUntil2.pollNext(cx))
        assertEquals(Poll.Ready(Yield.Value(2)), takeUntil2.pollNext(cx))
        assertNotNull(takeUntil2.takeFuture())
        val folded2 = takeUntil2.fold(0) { _, i -> i }
        assertEquals(Poll.Ready(10), folded2.poll(cx))

        // Verify takeFuture() returns null if stream is stopped
        val stream3 = streamIter((1..10).toList())
        val stopFut3 = makeStopFut(1)
        val takeUntil3 = stream3.takeUntil(stopFut3)
        assertEquals(Poll.Ready(Yield.Value(1)), takeUntil3.pollNext(cx))
        assertEquals(Poll.Ready(Yield.End), takeUntil3.pollNext(cx))
        assertNull(takeUntil3.takeFuture())
    }

    @Test
    fun chunksPanicOnCapZero() {
        assertFailsWith<IllegalArgumentException> {
            streamIter(listOf(1, 2)).chunks(0)
        }
    }

    @Test
    fun readyChunksPanicOnCapZero() {
        assertFailsWith<IllegalArgumentException> {
            streamIter(listOf(1, 2)).readyChunks(0)
        }
    }

    @Test
    fun readyChunks() {
        val (tx, rx) = io.github.kotlinmania.futures.channel.mpsc.channel<Int>(16)
        val s = rx.readyChunks(2)
        val cx = TaskContext()
        assertEquals(Poll.Pending, s.pollNext(cx))

        tx.trySend(1)
        assertEquals(Poll.Ready(Yield.Value(listOf(1))), s.pollNext(cx))

        tx.trySend(2)
        tx.trySend(3)
        tx.trySend(4)
        assertEquals(Poll.Ready(Yield.Value(listOf(2, 3))), s.pollNext(cx))
        assertEquals(Poll.Ready(Yield.Value(listOf(4))), s.pollNext(cx))
    }

    @Test
    fun selectWithStrategyDoesntTerminateEarly() {
        class SlowStream(
            private val timesShouldPoll: Int,
            var timesPolled: Int = 0,
        ) : Stream<Int> {
            override fun pollNext(context: TaskContext): Poll<Yield<Int>> {
                timesPolled += 1
                if (timesPolled % 2 == 0) {
                    context.wakeByRef()
                    return Poll.Pending
                }
                if (timesPolled >= timesShouldPoll) {
                    return Poll.Ready(Yield.End)
                }
                return Poll.Ready(Yield.Value(timesPolled))
            }
        }

        for (side in listOf(PollNext.Left, PollNext.Right)) {
            val timesShouldPoll = 10
            val slow = SlowStream(timesShouldPoll)
            val b = streamIter(listOf(10, 20))
            val selected = selectWithStrategy(slow, b) { side }
            val cx = TaskContext()
            while (true) {
                when (val p = selected.pollNext(cx)) {
                    is Poll.Ready<*> -> if (p.value is Yield.End) break
                    Poll.Pending -> {}
                }
                if (slow.timesPolled >= timesShouldPoll + 1) break
            }
            assertEquals(timesShouldPoll + 1, slow.timesPolled)
        }
    }



    @Test
    fun all() {
        fun isEven(n: Int): Boolean = n % 2 == 0
        val cx = TaskContext()

        val empty = streamIter(emptyList<Int>())
        assertEquals(Poll.Ready(true), empty.all(::isEven).poll(cx))

        val allEven = streamIter(listOf(2, 4, 6, 8))
        assertEquals(Poll.Ready(true), allEven.all(::isEven).poll(cx))

        val notAllEven = streamIter(listOf(2, 3, 4))
        assertEquals(Poll.Ready(false), notAllEven.all(::isEven).poll(cx))
    }

    @Test
    fun any() {
        fun isEven(n: Int): Boolean = n % 2 == 0
        val cx = TaskContext()

        val empty = streamIter(emptyList<Int>())
        assertEquals(Poll.Ready(false), empty.any(::isEven).poll(cx))

        val hasEven = streamIter(listOf(1, 2, 3))
        assertEquals(Poll.Ready(true), hasEven.any(::isEven).poll(cx))

        val noEven = streamIter(listOf(1, 3, 5))
        assertEquals(Poll.Ready(false), noEven.any(::isEven).poll(cx))
    }
}
