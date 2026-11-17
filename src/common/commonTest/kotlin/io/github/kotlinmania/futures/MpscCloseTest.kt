// port-lint: tests futures-channel/tests/mpsc-close.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.channel.mpsc.channel
import io.github.kotlinmania.futures.channel.mpsc.unbounded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MpscCloseTest {
    private val context = TaskContext()

    @Test
    fun smoke() {
        val (sender, receiver) = channel<Int>(1)
        val sendRes = sender.trySend(42)
        assertTrue(sendRes is Try.Ok)

        val pollRes = receiver.pollNext(context)
        assertTrue(pollRes is Poll.Ready)
        assertEquals(Yield.value(42), pollRes.value)
    }

    @Test
    fun multipleSendersDisconnect() {
        // Bounded channel
        run {
            val (tx1, rx) = channel<Int>(1)
            val tx2 = tx1.clone()
            val tx3 = tx1.clone()
            val tx4 = tx1.clone()

            tx1.disconnect()
            tx2.disconnect()
            tx3.disconnect()

            assertTrue(tx1.isClosed())
            assertTrue(tx2.isClosed())
            assertTrue(tx3.isClosed())
            assertFalse(tx4.isClosed())

            val sendRes = tx4.trySend(5)
            assertTrue(sendRes is Try.Ok)

            val poll1 = rx.pollNext(context)
            assertTrue(poll1 is Poll.Ready)
            assertEquals(Yield.value(5), poll1.value)

            tx4.disconnect()
            val poll2 = rx.pollNext(context)
            assertTrue(poll2 is Poll.Ready)
            assertEquals(Yield.End, poll2.value)
        }

        // Unbounded channel
        run {
            val (tx1, rx) = unbounded<Int>()
            val tx2 = tx1.clone()
            val tx3 = tx1.clone()
            val tx4 = tx1.clone()

            tx1.disconnect()
            tx2.disconnect()
            tx3.disconnect()

            assertTrue(tx1.isClosed())
            assertTrue(tx2.isClosed())
            assertTrue(tx3.isClosed())
            assertFalse(tx4.isClosed())

            val sendRes = tx4.trySend(5)
            assertTrue(sendRes is Try.Ok)

            val poll1 = rx.pollNext(context)
            assertTrue(poll1 is Poll.Ready)
            assertEquals(Yield.value(5), poll1.value)

            tx4.disconnect()
            val poll2 = rx.pollNext(context)
            assertTrue(poll2 is Poll.Ready)
            assertEquals(Yield.End, poll2.value)
        }
    }

    @Test
    fun multipleSendersCloseChannel() {
        // Bounded channel
        run {
            val (tx1, rx) = channel<Int>(1)
            val tx2 = tx1.clone()

            tx1.closeChannel()

            assertTrue(tx1.isClosed())
            assertTrue(tx2.isClosed())

            val sendRes = tx2.trySend(5)
            assertTrue(sendRes is Try.Err)
            assertTrue(sendRes.error.isDisconnected())

            val pollRes = rx.pollNext(context)
            assertTrue(pollRes is Poll.Ready)
            assertEquals(Yield.End, pollRes.value)
        }

        // Unbounded channel
        run {
            val (tx1, rx) = unbounded<Int>()
            val tx2 = tx1.clone()

            tx1.closeChannel()

            assertTrue(tx1.isClosed())
            assertTrue(tx2.isClosed())

            val sendRes = tx2.trySend(5)
            assertTrue(sendRes is Try.Err)
            assertTrue(sendRes.error.isDisconnected())

            val pollRes = rx.pollNext(context)
            assertTrue(pollRes is Poll.Ready)
            assertEquals(Yield.End, pollRes.value)
        }
    }

    @Test
    fun singleReceiverCloseClosesChannelAndDrains() {
        // Unbounded
        run {
            val (sender, receiver) = unbounded<Int>()
            val sendRes = sender.unboundedSend(100)
            assertTrue(sendRes is Try.Ok)

            receiver.close()
            assertTrue(sender.isClosed())

            val nextSend = sender.trySend(200)
            assertTrue(nextSend is Try.Err)
            assertTrue(nextSend.error.isDisconnected())
        }

        // Bounded
        run {
            val (sender, receiver) = channel<Int>(1)
            val sendRes = sender.trySend(100)
            assertTrue(sendRes is Try.Ok)

            receiver.close()
            assertTrue(sender.isClosed())

            val nextSend = sender.trySend(200)
            assertTrue(nextSend is Try.Err)
            assertTrue(nextSend.error.isDisconnected())
        }
    }

    @Test
    fun unboundedTryNextAfterNone() {
        val (tx, rx) = unbounded<String>()
        tx.disconnect()

        val res1 = rx.tryNext()
        assertTrue(res1 is Try.Ok)
        assertNull(res1.value)

        val res2 = rx.tryNext()
        assertTrue(res2 is Try.Ok)
        assertNull(res2.value)
    }

    @Test
    fun boundedTryNextAfterNone() {
        val (tx, rx) = channel<String>(17)
        tx.disconnect()

        val res1 = rx.tryNext()
        assertTrue(res1 is Try.Ok)
        assertNull(res1.value)

        val res2 = rx.tryNext()
        assertTrue(res2 is Try.Ok)
        assertNull(res2.value)
    }
}
