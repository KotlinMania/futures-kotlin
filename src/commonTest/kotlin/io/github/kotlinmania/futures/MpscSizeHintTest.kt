// port-lint: tests futures-channel/tests/mpsc-size_hint.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.channel.mpsc.channel
import io.github.kotlinmania.futures.channel.mpsc.unbounded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MpscSizeHintTest {
    @Test
    fun unboundedSizeHint() {
        val (tx, rx) = unbounded<Int>()
        assertEquals(SizeHint(0, null), rx.sizeHint())

        val send1 = tx.unboundedSend(1)
        assertTrue(send1 is Try.Ok)
        assertEquals(SizeHint(1, null), rx.sizeHint())

        val next1 = rx.tryNext()
        assertTrue(next1 is Try.Ok)
        assertEquals(1, next1.value)
        assertEquals(SizeHint(0, null), rx.sizeHint())

        val send2 = tx.unboundedSend(2)
        assertTrue(send2 is Try.Ok)
        val send3 = tx.unboundedSend(3)
        assertTrue(send3 is Try.Ok)
        assertEquals(SizeHint(2, null), rx.sizeHint())

        tx.disconnect()
        assertEquals(SizeHint(2, 2), rx.sizeHint())

        val next2 = rx.tryNext()
        assertTrue(next2 is Try.Ok)
        assertEquals(2, next2.value)
        assertEquals(SizeHint(1, 1), rx.sizeHint())

        val next3 = rx.tryNext()
        assertTrue(next3 is Try.Ok)
        assertEquals(3, next3.value)
        assertEquals(SizeHint(0, 0), rx.sizeHint())
    }

    @Test
    fun channelSizeHint() {
        val (tx, rx) = channel<Int>(10)
        assertEquals(SizeHint(0, null), rx.sizeHint())

        val send1 = tx.trySend(1)
        assertTrue(send1 is Try.Ok)
        assertEquals(SizeHint(1, null), rx.sizeHint())

        val next1 = rx.tryNext()
        assertTrue(next1 is Try.Ok)
        assertEquals(1, next1.value)
        assertEquals(SizeHint(0, null), rx.sizeHint())

        val send2 = tx.trySend(2)
        assertTrue(send2 is Try.Ok)
        val send3 = tx.trySend(3)
        assertTrue(send3 is Try.Ok)
        assertEquals(SizeHint(2, null), rx.sizeHint())

        tx.disconnect()
        assertEquals(SizeHint(2, 2), rx.sizeHint())

        val next2 = rx.tryNext()
        assertTrue(next2 is Try.Ok)
        assertEquals(2, next2.value)
        assertEquals(SizeHint(1, 1), rx.sizeHint())

        val next3 = rx.tryNext()
        assertTrue(next3 is Try.Ok)
        assertEquals(3, next3.value)
        assertEquals(SizeHint(0, 0), rx.sizeHint())
    }
}
