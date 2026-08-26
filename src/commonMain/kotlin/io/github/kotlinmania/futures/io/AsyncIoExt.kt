// port-lint: source futures-util/src/io/mod.rs
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
