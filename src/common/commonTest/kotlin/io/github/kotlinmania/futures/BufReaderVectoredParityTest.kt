package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.io.AsyncRead
import io.github.kotlinmania.futures.io.BufReader
import io.github.kotlinmania.futures.io.IoError
import io.github.kotlinmania.futures.io.IoErrorKind
import io.github.kotlinmania.futures.io.IoSliceMut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BufReaderVectoredParityTest {
    private class Reader(
        scalar: List<Poll<Try<ByteArray, IoError>>>,
        vector: List<Poll<Try<ByteArray, IoError>>>,
    ) : AsyncRead {
        private val scalar = scalar.toMutableList()
        private val vector = vector.toMutableList()
        val scalarRequests = mutableListOf<Int>()
        val vectorRequests = mutableListOf<List<Int>>()

        override fun pollRead(context: TaskContext, buf: ByteArray, offset: Int, length: Int): Poll<Try<Int, IoError>> {
            scalarRequests.add(length)
            return fill(context, scalar.removeAt(0), listOf(IoSliceMut(buf, offset, length)))
        }

        override fun pollReadVectored(context: TaskContext, bufs: List<IoSliceMut>): Poll<Try<Int, IoError>> {
            vectorRequests.add(bufs.map { it.length })
            return fill(context, vector.removeAt(0), bufs)
        }

        private fun fill(context: TaskContext, event: Poll<Try<ByteArray, IoError>>, bufs: List<IoSliceMut>): Poll<Try<Int, IoError>> =
            when (event) {
                Poll.Pending -> {
                    context.wakeByRef()
                    Poll.Pending
                }

                is Poll.Ready -> {
                    when (val result = event.value) {
                        is Try.Err -> {
                            Poll.Ready(result)
                        }

                        is Try.Ok -> {
                            var copied = 0
                            for (slice in bufs) {
                                val count = minOf(result.value.size - copied, slice.length)
                                result.value.copyInto(slice.buffer, slice.offset, copied, copied + count)
                                copied += count
                            }
                            check(copied == result.value.size)
                            Poll.Ready(Try.Ok(copied))
                        }
                    }
                }
            }
    }

    private fun bytes(text: String): Poll<Try<ByteArray, IoError>> = Poll.Ready(Try.Ok(text.encodeToByteArray()))

    private fun count(poll: Poll<Try<Int, IoError>>): Int = assertIs<Try.Ok<Int>>(assertIs<Poll.Ready<Try<Int, IoError>>>(poll).value).value

    @Test
    fun smallVectorsScatterBufferedBytesAcrossAllSliceRanges() {
        val inner = Reader(listOf(bytes("abcdef")), emptyList())
        val reader = BufReader.withCapacity(8, inner)
        val a = "____".encodeToByteArray()
        val b = "____".encodeToByteArray()
        val slices = listOf(IoSliceMut(a, 1, 2), IoSliceMut(ByteArray(0)), IoSliceMut(b, 1, 2))
        assertEquals(4, count(reader.pollReadVectored(TaskContext(), slices)))
        assertEquals("_ab_", a.decodeToString())
        assertEquals("_cd_", b.decodeToString())
        assertEquals("ef", reader.buffer().decodeToString())
        assertEquals(listOf(8), inner.scalarRequests)
        assertTrue(inner.vectorRequests.isEmpty())
        assertEquals(listOf(1, 0, 1), slices.map { it.offset })
        assertEquals(listOf(2, 0, 2), slices.map { it.length })
    }

    @Test
    fun emptyBufferWithCapacitySizedVectorsUsesInnerVectoredRead() {
        val inner = Reader(emptyList(), listOf(bytes("abcd")))
        val reader = BufReader.withCapacity(4, inner)
        val a = ByteArray(2)
        val b = ByteArray(2)
        assertEquals(4, count(reader.pollReadVectored(TaskContext(), listOf(IoSliceMut(a), IoSliceMut(b)))))
        assertEquals("ab", a.decodeToString())
        assertEquals("cd", b.decodeToString())
        assertEquals(listOf(listOf(2, 2)), inner.vectorRequests)
        assertTrue(inner.scalarRequests.isEmpty())
        assertEquals(0, reader.buffer().size)
    }

    @Test
    fun existingBufferedBytesPreventBypassEvenForLargeVectors() {
        val inner = Reader(listOf(bytes("abcd")), emptyList())
        val reader = BufReader.withCapacity(4, inner)
        assertIs<Poll.Ready<Try<ByteArray, IoError>>>(reader.pollFillBuf(TaskContext()))
        reader.consume(1)
        val a = "__".encodeToByteArray()
        val b = "____".encodeToByteArray()
        assertEquals(3, count(reader.pollReadVectored(TaskContext(), listOf(IoSliceMut(a), IoSliceMut(b)))))
        assertEquals("bc", a.decodeToString())
        assertEquals("d___", b.decodeToString())
        assertEquals(0, reader.buffer().size)
        assertEquals(listOf(4), inner.scalarRequests)
        assertTrue(inner.vectorRequests.isEmpty())
    }

    @Test
    fun directVectoredPendingResumesWithoutAScalarRead() {
        val inner = Reader(emptyList(), listOf(Poll.Pending, bytes("abcd")))
        val reader = BufReader.withCapacity(4, inner)
        val slices = listOf(IoSliceMut(ByteArray(2)), IoSliceMut(ByteArray(2)))
        assertSame(Poll.Pending, reader.pollReadVectored(TaskContext(), slices))
        assertEquals(4, count(reader.pollReadVectored(TaskContext(), slices)))
        assertEquals(listOf(listOf(2, 2), listOf(2, 2)), inner.vectorRequests)
        assertTrue(inner.scalarRequests.isEmpty())
    }

    @Test
    fun directVectoredErrorPreservesTheOriginalError() {
        val failure = IoError(IoErrorKind.TimedOut, "timeout")
        val inner = Reader(emptyList(), listOf(Poll.Ready(Try.Err(failure))))
        val reader = BufReader.withCapacity(4, inner)
        val result = assertIs<Poll.Ready<Try<Int, IoError>>>(reader.pollReadVectored(TaskContext(), listOf(IoSliceMut(ByteArray(2)), IoSliceMut(ByteArray(2))))).value
        assertSame(failure, assertIs<Try.Err<IoError>>(result).error)
        assertTrue(inner.scalarRequests.isEmpty())
        assertEquals(0, reader.buffer().size)
    }

    @Test
    fun emptyVectorsStillFillAnEmptyNonzeroCapacityBuffer() {
        val inner = Reader(listOf(bytes("ab")), emptyList())
        val reader = BufReader.withCapacity(4, inner)
        assertEquals(0, count(reader.pollReadVectored(TaskContext(), emptyList())))
        assertEquals("ab", reader.buffer().decodeToString())
        assertEquals(listOf(4), inner.scalarRequests)
    }
}
