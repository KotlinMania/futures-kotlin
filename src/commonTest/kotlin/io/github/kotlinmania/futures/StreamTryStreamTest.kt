// port-lint: tests futures/tests/stream_try_stream.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals

class StreamTryStreamTest {
    @Test
    fun tryFilterMapAfterErr() {
        val cx = TaskContext()
        val s = streamIter(listOf<Try<Int, Int>>(Try.Ok(1), Try.Ok(2), Try.Ok(3)))
            .asTryStream()
            .tryFilterMap<Int, Int, Unit> { v ->
                Future { Poll.Ready(Try.Err(v)) }
            }

        val p = s.pollNext(cx)
        assertEquals(Poll.Ready(Yield.Value(Try.Err(1))), p)
    }

    @Test
    fun trySkipWhileAfterErr() {
        val cx = TaskContext()
        val s = streamIter(listOf<Try<Int, String>>(Try.Ok(1), Try.Ok(2), Try.Ok(3)))
            .asTryStream()
            .trySkipWhile { _ ->
                Future { Poll.Ready(Try.Err("error")) }
            }

        val p = s.pollNext(cx)
        assertEquals(Poll.Ready(Yield.Value(Try.Err("error"))), p)
    }

    @Test
    fun tryTakeWhileAfterErr() {
        val cx = TaskContext()
        val s = streamIter(listOf<Try<Int, String>>(Try.Ok(1), Try.Ok(2), Try.Ok(3)))
            .asTryStream()
            .tryTakeWhile { _ ->
                Future { Poll.Ready(Try.Err("error")) }
            }

        val p = s.pollNext(cx)
        assertEquals(Poll.Ready(Yield.Value(Try.Err("error"))), p)
    }

    @Test
    fun tryAll() {
        val cx = TaskContext()

        val emptySt = streamIter(emptyList<Try<Int, String>>()).asTryStream()
        val allEmpty = emptySt.tryAll { n -> Future { Poll.Ready(n % 2 == 0) } }
        assertEquals(Poll.Ready(Try.Ok(true)), allEmpty.poll(cx))

        val st1 = streamIter(listOf<Try<Int, String>>(Try.Ok(2), Try.Ok(4), Try.Ok(6), Try.Ok(8))).asTryStream()
        val all1 = st1.tryAll { n -> Future { Poll.Ready(n % 2 == 0) } }
        assertEquals(Poll.Ready(Try.Ok(true)), all1.poll(cx))

        val st2 = streamIter(listOf<Try<Int, String>>(Try.Ok(2), Try.Ok(3), Try.Ok(4))).asTryStream()
        val all2 = st2.tryAll { n -> Future { Poll.Ready(n % 2 == 0) } }
        assertEquals(Poll.Ready(Try.Ok(false)), all2.poll(cx))

        val st3 = streamIter(listOf<Try<Int, String>>(Try.Ok(2), Try.Ok(4), Try.Err("err"), Try.Ok(8))).asTryStream()
        val all3 = st3.tryAll { n -> Future { Poll.Ready(n % 2 == 0) } }
        assertEquals(Poll.Ready(Try.Err("err")), all3.poll(cx))
    }

    @Test
    fun tryAny() {
        val cx = TaskContext()

        val emptySt = streamIter(emptyList<Try<Int, String>>()).asTryStream()
        val anyEmpty = emptySt.tryAny { n -> Future { Poll.Ready(n % 2 == 0) } }
        assertEquals(Poll.Ready(Try.Ok(false)), anyEmpty.poll(cx))

        val st1 = streamIter(listOf<Try<Int, String>>(Try.Ok(1), Try.Ok(2), Try.Ok(3))).asTryStream()
        val any1 = st1.tryAny { n -> Future { Poll.Ready(n % 2 == 0) } }
        assertEquals(Poll.Ready(Try.Ok(true)), any1.poll(cx))

        val st2 = streamIter(listOf<Try<Int, String>>(Try.Ok(1), Try.Ok(3), Try.Ok(5))).asTryStream()
        val any2 = st2.tryAny { n -> Future { Poll.Ready(n % 2 == 0) } }
        assertEquals(Poll.Ready(Try.Ok(false)), any2.poll(cx))

        val st3 = streamIter(listOf<Try<Int, String>>(Try.Ok(1), Try.Ok(3), Try.Err("err"), Try.Ok(8))).asTryStream()
        val any3 = st3.tryAny { n -> Future { Poll.Ready(n % 2 == 0) } }
        assertEquals(Poll.Ready(Try.Err("err")), any3.poll(cx))
    }

    @Test
    fun tryCollectAndTryConcat() {
        val cx = TaskContext()

        val stOk = streamIter(listOf<Try<Int, String>>(Try.Ok(1), Try.Ok(2), Try.Ok(3))).asTryStream()
        val collectOk = stOk.tryCollect()
        assertEquals(Poll.Ready(Try.Ok(listOf(1, 2, 3))), collectOk.poll(cx))

        val stErr = streamIter(listOf<Try<Int, String>>(Try.Ok(1), Try.Err("failed"), Try.Ok(3))).asTryStream()
        val collectErr = stErr.tryCollect()
        assertEquals(Poll.Ready(Try.Err("failed")), collectErr.poll(cx))

        val concatSt = streamIter(listOf<Try<List<Int>, String>>(Try.Ok(listOf(1, 2)), Try.Ok(listOf(3, 4)))).asTryStream()
        val concat = concatSt.tryConcat()
        assertEquals(Poll.Ready(Try.Ok(listOf(1, 2, 3, 4))), concat.poll(cx))
    }

    @Test
    fun tryFoldAndTryForEach() {
        val cx = TaskContext()

        val st = streamIter(listOf<Try<Int, String>>(Try.Ok(1), Try.Ok(2), Try.Ok(3))).asTryStream()
        val fold = st.tryFold(0) { acc, item ->
            Future { Poll.Ready(Try.Ok(acc + item)) }
        }
        assertEquals(Poll.Ready(Try.Ok(6)), fold.poll(cx))

        var sum = 0
        val forEachSt = streamIter(listOf<Try<Int, String>>(Try.Ok(10), Try.Ok(20))).asTryStream()
        val forEach = forEachSt.tryForEach { item ->
            sum += item
            Future { Poll.Ready(Try.Ok(Unit)) }
        }
        assertEquals(Poll.Ready(Try.Ok(Unit)), forEach.poll(cx))
        assertEquals(30, sum)
    }

    @Test
    fun tryUnfold() {
        val cx = TaskContext()
        val st = tryStreamUnfold<Int, Int, String>(0) { state ->
            Future {
                if (state <= 2) {
                    Poll.Ready(Try.Ok(Pair(state * 2, state + 1)))
                } else {
                    Poll.Ready(Try.Ok(null))
                }
            }
        }.asTryStream()

        val collected = st.tryCollect()
        assertEquals(Poll.Ready(Try.Ok(listOf(0, 2, 4))), collected.poll(cx))
    }
}
