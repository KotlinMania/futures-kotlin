// port-lint: tests future/poll_immediate.rs
package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PollImmediateTest {
    @Test
    fun testPollImmediateReady() {
        val f = ready(42)
        val imm = pollImmediate(f)
        val res = imm.poll(TaskContext())
        assertTrue(res is Poll.Ready<*>)
        val innerPoll = (res as Poll.Ready<Poll<Int>>).value
        assertTrue(innerPoll is Poll.Ready<*>)
        assertEquals(42, (innerPoll as Poll.Ready<Int>).value)
    }

    @Test
    fun testPollImmediatePending() {
        val f = pending<Int>()
        val imm = pollImmediate(f)
        val res = imm.poll(TaskContext())
        assertTrue(res is Poll.Ready<*>)
        val innerPoll = (res as Poll.Ready<Poll<Int>>).value
        assertTrue(innerPoll is Poll.Pending)
    }
}
