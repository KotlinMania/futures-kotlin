// port-lint: tests futures/tests/future_basic_combinators.rs
package io.github.kotlinmania.futures.future

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import io.github.kotlinmania.futures.andThen
import io.github.kotlinmania.futures.map
import io.github.kotlinmania.futures.mapErr
import io.github.kotlinmania.futures.mapOk
import io.github.kotlinmania.futures.orElse
import io.github.kotlinmania.futures.ready
import io.github.kotlinmania.futures.then
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FutureBasicCombinatorsTest {
    @Test
    fun basicFutureCombinators() {
        val list = mutableListOf<Int>()

        val fut =
            ready(1)
                .then { x ->
                    list.add(x)
                    list.add(2)
                    ready(3)
                }.map { x ->
                    list.add(x)
                    list.add(4)
                    5
                }.map { x ->
                    list.add(x)
                }

        assertEquals(0, list.size)
        val cx = TaskContext()
        val res = fut.poll(cx)
        assertIs<Poll.Ready<Unit>>(res)

        assertEquals(listOf(1, 2, 3, 4, 5), list)
    }

    @Test
    fun basicTryFutureCombinators() {
        val list = mutableListOf<Int>()

        val fut =
            ready(Try.ok(1))
                .andThen { x ->
                    list.add(x)
                    list.add(2)
                    ready(Try.ok(3))
                }.orElse { x ->
                    list.add(x)
                    list.add(-1)
                    ready(Try.ok(-1))
                }.mapOk { x ->
                    list.add(x)
                    list.add(4)
                    5
                }.mapErr { x ->
                    list.add(x)
                    list.add(-1)
                    -1
                }.map { x ->
                    list.add((x as Try.Ok).value)
                    list.add(6)
                    Try.err(7)
                }.andThen { x ->
                    list.add(x)
                    list.add(-1)
                    ready(Try.err(-1))
                }.orElse { x ->
                    list.add(x)
                    list.add(8)
                    ready(Try.err(9))
                }.mapOk { x ->
                    list.add(x)
                    list.add(-1)
                    -1
                }.mapErr { x ->
                    list.add(x)
                    list.add(10)
                    11
                }.map { x ->
                    list.add((x as Try.Err).error)
                    list.add(12)
                }

        assertEquals(0, list.size)
        val cx = TaskContext()
        val res = fut.poll(cx)
        assertIs<Poll.Ready<Unit>>(res)

        assertEquals((1..12).toList(), list)
    }
}
