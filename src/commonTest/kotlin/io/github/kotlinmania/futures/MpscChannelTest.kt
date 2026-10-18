// port-lint: tests futures-channel/tests/channel.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.channel.mpsc.channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MpscChannelTest {
    private val context = TaskContext()

    @Test
    fun sequence() {
        val (tx, rx) = channel<Int>(20)
        val amt = 20
        for (x in 0 until amt) {
            val res = tx.trySend(amt - x)
            assertTrue(res is Try.Ok)
        }

        val list = mutableListOf<Int>()
        for (i in 0 until amt) {
            val poll = rx.pollNext(context)
            assertTrue(poll is Poll.Ready)
            val y = poll.value
            assertTrue(y is Yield.Value)
            list.add(y.value)
        }

        val expected = (amt downTo 1).toList()
        assertEquals(expected, list)

        tx.disconnect()
        val endPoll = rx.pollNext(context)
        assertTrue(endPoll is Poll.Ready)
        assertEquals(Yield.End, endPoll.value)
    }

    @Test
    fun dropSender() {
        val (tx, rx) = channel<Int>(1)
        tx.disconnect()
        val poll = rx.pollNext(context)
        assertTrue(poll is Poll.Ready)
        assertEquals(Yield.End, poll.value)
    }

    @Test
    fun dropRx() {
        val (tx, rx) = channel<Int>(1)
        val send1 = tx.trySend(1)
        assertTrue(send1 is Try.Ok)

        rx.close()
        val send2 = tx.trySend(2)
        assertTrue(send2 is Try.Err)
        assertTrue(send2.error.isDisconnected())
    }

    @Test
    fun dropOrder() {
        val (tx, rx) = channel<String>(1)
        val send1 = tx.trySend("first")
        assertTrue(send1 is Try.Ok)

        rx.close()
        assertTrue(tx.isClosed())

        val send2 = tx.trySend("second")
        assertTrue(send2 is Try.Err)
        assertTrue(send2.error.isDisconnected())
    }
}
