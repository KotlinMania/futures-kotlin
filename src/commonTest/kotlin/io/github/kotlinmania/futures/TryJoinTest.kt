// port-lint: tests futures-util/tests/try_join.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TryJoinTest {
    @Test
    fun testTryJoinBothOk() {
        val f1 = ready(Try.ok(1))
        val f2 = ready(Try.ok("a"))
        val joined = tryJoin(f1, f2)

        val res = joined.poll(TaskContext())
        assertTrue(res is Poll.Ready<*>)
        val v = (res as Poll.Ready<Try<Pair<Int, String>, Nothing>>).value
        assertTrue(v is Try.Ok)
        assertEquals(Pair(1, "a"), v.value)
    }

    @Test
    fun testTryJoinFirstErr() {
        val f1: Future<Try<Int, String>> = ready(Try.err("fail"))
        val f2: Future<Try<String, String>> = ready(Try.ok("a"))
        val joined = tryJoin(f1, f2)

        val res = joined.poll(TaskContext())
        assertTrue(res is Poll.Ready<*>)
        val v = (res as Poll.Ready<Try<Pair<Int, String>, String>>).value
        assertTrue(v is Try.Err)
        assertEquals("fail", v.error)
    }

    @Test
    fun testTryJoin3AndTryJoinAll() {
        val f1 = ready(Try.ok(1))
        val f2 = ready(Try.ok(2))
        val f3 = ready(Try.ok(3))
        val joined3 = tryJoin3(f1, f2, f3)

        val res3 = joined3.poll(TaskContext())
        assertTrue(res3 is Poll.Ready<*>)
        val v3 = (res3 as Poll.Ready<Try<Triple<Int, Int, Int>, Nothing>>).value
        assertTrue(v3 is Try.Ok)
        assertEquals(Triple(1, 2, 3), v3.value)

        val allList = listOf(ready(Try.ok(10)), ready(Try.ok(20)))
        val joinedAll = tryJoinAll(allList)
        val resAll = joinedAll.poll(TaskContext())
        assertTrue(resAll is Poll.Ready<*>)
        val vAll = (resAll as Poll.Ready<Try<List<Int>, Nothing>>).value
        assertTrue(vAll is Try.Ok)
        assertEquals(listOf(10, 20), vAll.value)
    }

    @Test
    fun tryJoinNeverError() {
        val f1: Future<Try<Unit, Nothing>> = ready(Try.ok(Unit))
        val f2: Future<Try<Unit, Nothing>> = ready(Try.ok(Unit))
        val joined = tryJoin(f1, f2)
        val res = joined.poll(TaskContext())
        assertTrue(res is Poll.Ready<*>)
        val v = (res as Poll.Ready<Try<Pair<Unit, Unit>, Nothing>>).value
        assertTrue(v is Try.Ok)
        assertEquals(Pair(Unit, Unit), v.value)
    }

    @Test
    fun tryJoinNeverOk() {
        val f1: Future<Try<Nothing, Unit>> = ready(Try.err(Unit))
        val f2: Future<Try<Nothing, Unit>> = ready(Try.err(Unit))
        val joined = tryJoin(f1, f2)
        val res = joined.poll(TaskContext())
        assertTrue(res is Poll.Ready<*>)
        val v = (res as Poll.Ready<Try<Pair<Nothing, Nothing>, Unit>>).value
        assertTrue(v is Try.Err)
        assertEquals(Unit, v.error)
    }
}

