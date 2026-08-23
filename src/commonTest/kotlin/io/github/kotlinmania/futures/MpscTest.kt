// port-lint: tests futures-channel/tests/mpsc.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.channel.mpsc.TrySendError
import io.github.kotlinmania.futures.channel.mpsc.channel
import io.github.kotlinmania.futures.channel.mpsc.unbounded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MpscTest {
    @Test
    fun sendRecv() {
        val (tx, rx) = channel<Int>(16)
        val cx = TaskContext()

        assertEquals(Poll.Ready(SinkOutcome.ready()), tx.pollReady(cx))
        assertEquals(SinkOutcome.ready(), tx.startSend(1))
        tx.disconnect()

        val next = rx.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(next)
        assertEquals(Yield.value(1), next.value)

        val end = rx.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(end)
        assertEquals(Yield.end(), end.value)
    }

    @Test
    fun sendRecvNoBuffer() {
        val (tx, rx) = channel<Int>(0)
        val cx = TaskContext()

        assertEquals(Poll.Ready(SinkOutcome.ready()), tx.pollFlush(cx))
        assertEquals(Poll.Ready(SinkOutcome.ready()), tx.pollReady(cx))

        // Send first message
        assertEquals(SinkOutcome.ready(), tx.startSend(1))

        // No room in buffer, so startSend fails or pollReady is pending
        val fullErr = tx.startSend(0)
        assertIs<SinkOutcome.Err<*>>(fullErr)

        // Take the value
        assertEquals(Poll.Ready(Yield.value(1)), rx.pollNext(cx))
        assertEquals(Poll.Ready(SinkOutcome.ready()), tx.pollReady(cx))

        // Send second message
        assertEquals(SinkOutcome.ready(), tx.startSend(2))

        // Take the value
        assertEquals(Poll.Ready(Yield.value(2)), rx.pollNext(cx))
        assertEquals(Poll.Ready(SinkOutcome.ready()), tx.pollReady(cx))
    }

    @Test
    fun sendSharedRecv() {
        val (tx1, rx) = channel<Int>(16)
        val tx2 = tx1.clone()
        val cx = TaskContext()

        assertTrue(tx1.trySend(1) is Try.Ok)
        assertEquals(Poll.Ready(Yield.value(1)), rx.pollNext(cx))

        assertTrue(tx2.trySend(2) is Try.Ok)
        assertEquals(Poll.Ready(Yield.value(2)), rx.pollNext(cx))
    }

    @Test
    fun unboundedSizeHint() {
        val (tx, rx) = unbounded<Int>()
        assertEquals(SizeHint(0, null), rx.sizeHint())
        assertTrue(tx.unboundedSend(1) is Try.Ok)
        assertEquals(SizeHint(1, null), rx.sizeHint())

        val res1 = rx.tryNext()
        assertIs<Try.Ok<Int?>>(res1)
        assertEquals(1, res1.value)
        assertEquals(SizeHint(0, null), rx.sizeHint())

        assertTrue(tx.unboundedSend(2) is Try.Ok)
        assertTrue(tx.unboundedSend(3) is Try.Ok)
        assertEquals(SizeHint(2, null), rx.sizeHint())

        tx.disconnect()
        assertEquals(SizeHint(2, 2), rx.sizeHint())

        val res2 = rx.tryNext()
        assertIs<Try.Ok<Int?>>(res2)
        assertEquals(2, res2.value)
        assertEquals(SizeHint(1, 1), rx.sizeHint())

        val res3 = rx.tryNext()
        assertIs<Try.Ok<Int?>>(res3)
        assertEquals(3, res3.value)
        assertEquals(SizeHint(0, 0), rx.sizeHint())
    }

    @Test
    fun channelSizeHint() {
        val (tx, rx) = channel<Int>(10)
        assertEquals(SizeHint(0, null), rx.sizeHint())
        assertTrue(tx.trySend(1) is Try.Ok)
        assertEquals(SizeHint(1, null), rx.sizeHint())

        val res1 = rx.tryNext()
        assertIs<Try.Ok<Int?>>(res1)
        assertEquals(1, res1.value)
        assertEquals(SizeHint(0, null), rx.sizeHint())

        assertTrue(tx.trySend(2) is Try.Ok)
        assertTrue(tx.trySend(3) is Try.Ok)
        assertEquals(SizeHint(2, null), rx.sizeHint())

        tx.disconnect()
        assertEquals(SizeHint(2, 2), rx.sizeHint())

        val res2 = rx.tryNext()
        assertIs<Try.Ok<Int?>>(res2)
        assertEquals(2, res2.value)
        assertEquals(SizeHint(1, 1), rx.sizeHint())

        val res3 = rx.tryNext()
        assertIs<Try.Ok<Int?>>(res3)
        assertEquals(3, res3.value)
        assertEquals(SizeHint(0, 0), rx.sizeHint())
    }

    @Test
    fun multipleSendersDisconnect() {
        val (tx1, rx) = channel<Int>(1)
        val tx2 = tx1.clone()
        val tx3 = tx1.clone()
        val tx4 = tx1.clone()
        val cx = TaskContext()

        tx1.disconnect()
        tx2.disconnect()
        tx3.closeChannel()

        assertTrue(tx1.isClosed())
        assertTrue(tx3.isClosed())

        val sendRes = tx4.trySend(5)
        // Since closeChannel closes the whole channel:
        assertTrue(sendRes is Try.Err)
        assertTrue(sendRes.error.isDisconnected())

        val next = rx.tryNext()
        assertIs<Try.Ok<Int?>>(next)
        assertNull(next.value)
    }

    @Test
    fun multipleSendersCloseChannel() {
        val (tx1, rx) = channel<Int>(1)
        val tx2 = tx1.clone()
        val cx = TaskContext()

        tx1.closeChannel()

        assertTrue(tx1.isClosed())
        assertTrue(tx2.isClosed())

        val sendRes = tx2.trySend(5)
        assertIs<Try.Err<TrySendError<Int>>>(sendRes)
        assertTrue(sendRes.error.isDisconnected())

        val next = rx.pollNext(cx)
        assertEquals(Poll.Ready(Yield.end()), next)
    }

    @Test
    fun unboundedTryNextAfterNone() {
        val (tx, rx) = unbounded<String>()
        tx.disconnect()

        val next1 = rx.tryNext()
        assertIs<Try.Ok<String?>>(next1)
        assertNull(next1.value)

        val next2 = rx.tryNext()
        assertIs<Try.Ok<String?>>(next2)
        assertNull(next2.value)
    }

    @Test
    fun boundedTryNextAfterNone() {
        val (tx, rx) = channel<String>(17)
        tx.disconnect()

        val next1 = rx.tryNext()
        assertIs<Try.Ok<String?>>(next1)
        assertNull(next1.value)

        val next2 = rx.tryNext()
        assertIs<Try.Ok<String?>>(next2)
        assertNull(next2.value)
    }

    @Test
    fun sameReceiverCheck() {
        val (tx1, _) = channel<Int>(1)
        val tx2 = tx1.clone()
        val (tx3, _) = channel<Int>(1)

        assertTrue(tx1.sameReceiver(tx2))
        assertFalse(tx1.sameReceiver(tx3))

        val (utx1, _) = unbounded<Int>()
        val utx2 = utx1.clone()
        val (utx3, _) = unbounded<Int>()

        assertTrue(utx1.sameReceiver(utx2))
        assertFalse(utx1.sameReceiver(utx3))
    }
}
