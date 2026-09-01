// port-lint: source io/mod.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import kotlin.native.HiddenFromObjC

/**
 * Creates a future which will read some bytes into [buf].
 */
@HiddenFromObjC
public fun AsyncRead.read(
    buf: ByteArray,
    offset: Int = 0,
    length: Int = buf.size - offset,
): Read = Read(this, buf, offset, length)

/**
 * Creates a future which will read the exact number of bytes required to fill [buf].
 */
@HiddenFromObjC
public fun AsyncRead.readExact(
    buf: ByteArray,
    offset: Int = 0,
    length: Int = buf.size - offset,
): ReadExact = ReadExact(this, buf, offset, length)

/**
 * Creates a future which will read all bytes until EOF, appending them to [outBuf].
 */
@HiddenFromObjC
public fun AsyncRead.readToEnd(
    outBuf: MutableList<Byte>,
): ReadToEnd = ReadToEnd(this, outBuf)

/**
 * Creates a future which will read all bytes until EOF, appending them as UTF-8 string to [outBuf].
 */
@HiddenFromObjC
public fun AsyncRead.readToString(
    outBuf: StringBuilder,
): ReadToString = ReadToString(this, outBuf)

/**
 * Transforms this reader into another reader which will read up to [limit] bytes.
 */
@HiddenFromObjC
public fun <R : AsyncRead> R.take(limit: Long): Take<R> = Take(this, limit)

/**
 * Chains two readers together, reading from this reader until EOF, then reading from [next].
 */
@HiddenFromObjC
public fun <T : AsyncRead, U : AsyncRead> T.chain(next: U): Chain<T, U> = Chain(this, next)

/**
 * Creates a future which will read all bytes until the delimiter [byte] is found.
 */
@HiddenFromObjC
public fun AsyncBufRead.readUntil(
    byte: Byte,
    outBuf: MutableList<Byte>,
): ReadUntil = ReadUntil(this, byte, outBuf)

/**
 * Creates a future which will read all bytes until a newline (0x0A) is reached, appending them to [outBuf].
 */
@HiddenFromObjC
public fun AsyncBufRead.readLine(
    outBuf: StringBuilder,
): ReadLine = ReadLine(this, outBuf)

/**
 * Returns a stream over the lines of this reader.
 */
@HiddenFromObjC
public fun <R : AsyncBufRead> R.lines(): Lines<R> = Lines(this)

/**
 * Creates a future which will fill the internal buffer of this [AsyncBufRead].
 */
@HiddenFromObjC
public fun AsyncBufRead.fillBuf(): FillBuf = FillBuf(this)

/**
 * Creates a future which will write some bytes from [buf] into this writer.
 */
@HiddenFromObjC
public fun AsyncWrite.write(
    buf: ByteArray,
    offset: Int = 0,
    length: Int = buf.size - offset,
): Write = Write(this, buf, offset, length)

/**
 * Creates a future which will write all bytes in [buf] into this writer.
 */
@HiddenFromObjC
public fun AsyncWrite.writeAll(
    buf: ByteArray,
    offset: Int = 0,
    length: Int = buf.size - offset,
): WriteAll = WriteAll(this, buf, offset, length)

/**
 * Creates a future which will flush this writer.
 */
@HiddenFromObjC
public fun AsyncWrite.flush(): Flush = Flush(this)

/**
 * Creates a future which will close this writer.
 */
@HiddenFromObjC
public fun AsyncWrite.close(): Close = Close(this)

/**
 * Wraps an [AsyncWrite] in a [BufWriter] with the specified capacity.
 */
@HiddenFromObjC
public fun <W : AsyncWrite> W.bufWriter(capacity: Int = DEFAULT_BUF_SIZE): BufWriter<W> =
    BufWriter(this, capacity)

/**
 * Wraps an [AsyncWrite] in a [LineWriter] with the specified capacity.
 */
@HiddenFromObjC
public fun <W : AsyncWrite> W.lineWriter(capacity: Int = 1024): LineWriter<W> =
    LineWriter(this, capacity)

/**
 * Wraps an [AsyncWrite] as a [Sink] of byte arrays.
 */
@HiddenFromObjC
public fun <W : AsyncWrite> W.intoSink(): IntoSink<W> = IntoSink(this)

/**
 * Creates a future which seeks to [pos] on this [AsyncSeek].
 */
@HiddenFromObjC
public fun <S : AsyncSeek> S.seek(pos: SeekFrom): Seek<S> = Seek(this, pos)

/**
 * Creates a future which copies all bytes from this [AsyncBufRead] into [writer], with an [io.github.kotlinmania.futures.AbortHandle].
 */
@HiddenFromObjC
public fun AsyncBufRead.copyBufAbortable(writer: AsyncWrite): Pair<CopyBufAbortable, io.github.kotlinmania.futures.AbortHandle> =
    io.github.kotlinmania.futures.io
        .copyBufAbortable(this, writer)
