package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TryForEachStateParityTest {
    private fun check(
        input: List<Poll<Yield<Try<Int, String?>>>>,
        actions: List<String>,
        expected: List<String>,
        events: List<String>,
        expectedStream: Int,
        expectedAction: Int,
    ) {
        val trace = mutableListOf<String>()
        var streamPolls = 0
        var actionPolls = 0
        val stream =
            object : TryStream<Int, String?> {
                override fun pollNext(context: TaskContext): Poll<Yield<Try<Int, String?>>> {
                    trace.add("stream:${++streamPolls}")
                    return input[streamPolls - 1]
                }
            }
        val future =
            stream.tryForEach { value ->
                trace.add("make:$value")
                var index = 0
                Future<Try<Unit, String?>> {
                    trace.add("action:${++actionPolls}")
                    when (val step = actions[index++]) {
                        "pending" -> Poll.Pending
                        "ok" -> Poll.Ready(Try.Ok(Unit))
                        "panic" -> error("action panic")
                        else -> Poll.Ready(Try.Err(if (step == "err:null") null else step.removePrefix("err:")))
                    }
                }
            }
        val actual =
            expected.map { expectedPoll ->
                try {
                    when (val polled = future.poll(TaskContext())) {
                        Poll.Pending -> {
                            "pending"
                        }

                        is Poll.Ready -> {
                            when (val result = polled.value) {
                                is Try.Ok -> {
                                    "ok"
                                }

                                is Try.Err -> {
                                    val payload = if (expectedPoll == "err:null") null else expectedPoll.removePrefix("err:")
                                    assertEquals(payload, result.error)
                                    "err:${result.error ?: "null"}"
                                }
                            }
                        }
                    }
                } catch (exception: IllegalStateException) {
                    "panic:${exception.message}"
                }
            }
        assertEquals(expected, actual)
        assertEquals(events, trace)
        assertEquals(expectedStream, streamPolls)
        assertEquals(expectedAction, actionPolls)
    }

    @Test
    fun failedCallbackRetained() {
        check(listOf(Poll.Ready(Yield.Value(Try.Ok(1))), Poll.Ready(Yield.End)), listOf("err:fail", "err:again"), listOf("err:fail", "err:again"), listOf("stream:1", "make:1", "action:1", "action:2"), 1, 2)
    }

    @Test
    fun errorPendingThenSuccess() {
        check(listOf(Poll.Ready(Yield.Value(Try.Ok(1))), Poll.Ready(Yield.End)), listOf("err:fail", "pending", "ok"), listOf("err:fail", "pending", "ok"), listOf("stream:1", "make:1", "action:1", "action:2", "action:3", "stream:2"), 2, 3)
    }

    @Test
    fun failedFuturePanicOnRepeat() {
        check(listOf(Poll.Ready(Yield.Value(Try.Ok(1))), Poll.Ready(Yield.End)), listOf("err:fail", "panic"), listOf("err:fail", "panic:action panic"), listOf("stream:1", "make:1", "action:1", "action:2"), 1, 2)
    }

    @Test
    fun nullableErrorAfterPending() {
        check(listOf(Poll.Ready(Yield.Value(Try.Ok(1))), Poll.Ready(Yield.End)), listOf("pending", "err:null", "err:null"), listOf("pending", "err:null", "err:null"), listOf("stream:1", "make:1", "action:1", "action:2", "action:3"), 1, 3)
    }

    @Test
    fun streamErrorContinuation() {
        check(listOf(Poll.Ready(Yield.Value(Try.Err("stream"))), Poll.Ready(Yield.Value(Try.Ok(1))), Poll.Ready(Yield.End)), listOf("ok"), listOf("err:stream", "ok"), listOf("stream:1", "stream:2", "make:1", "action:1", "stream:3"), 3, 1)
    }

    @Test
    fun outerAndInnerPending() {
        check(listOf(Poll.Pending, Poll.Ready(Yield.Value(Try.Ok(1))), Poll.Ready(Yield.End)), listOf("pending", "ok"), listOf("pending", "pending", "ok"), listOf("stream:1", "stream:2", "make:1", "action:1", "action:2", "stream:3"), 3, 2)
    }

    @Test
    fun canonicalFuseAfterError() {
        val future =
            streamIter(listOf<Try<Int, String?>>(Try.Ok(1)))
                .asTryStream()
                .tryForEach { ready<Try<Unit, String?>>(Try.Err("fail")) }
                .fuse()
        assertFalse(future.isTerminated())
        assertEquals(Poll.Ready(Try.Err("fail")), future.poll(TaskContext()))
        assertTrue(future.isTerminated())
        assertEquals(Poll.Pending, future.poll(TaskContext()))
    }

    @Test
    fun unwrappedAdaptorHasNoFusedPromise() {
        val future: Any =
            streamIter(emptyList<Try<Int, String?>>())
                .asTryStream()
                .tryForEach { ready<Try<Unit, String?>>(Try.Ok(Unit)) }
        assertFalse(future is FusedFuture<*>)
    }
}
