// port-lint: source futures-io/src/lib.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.io

import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Kind of I/O error encountered during asynchronous I/O operations.
 */
public enum class IoErrorKind {
    NotFound,
    PermissionDenied,
    ConnectionRefused,
    ConnectionReset,
    ConnectionAborted,
    NotConnected,
    AddrInUse,
    AddrNotAvailable,
    BrokenPipe,
    AlreadyExists,
    WouldBlock,
    InvalidInput,
    InvalidData,
    TimedOut,
    WriteZero,
    Interrupted,
    UnexpectedEof,
    Other,
}

/**
 * Error representation for asynchronous I/O operations.
 */
public class IoError(
    public val kind: IoErrorKind,
    override val message: String? = null,
    override val cause: Throwable? = null,
) : Exception(message ?: kind.name, cause) {
    public constructor(kind: IoErrorKind, message: String) : this(kind, message, null)
    public constructor(message: String) : this(IoErrorKind.Other, message, null)

    override fun toString(): String = "IoError(kind=$kind, message=$message)"

    public companion object {
        public fun from(kind: IoErrorKind, message: String? = null): IoError =
            IoError(kind, message)
    }
}

/**
 * Enumeration of possible methods to seek within an I/O stream.
 */
public sealed class SeekFrom {
    /** Sets the offset to the provided number of bytes from the start. */
    public data class Start(
        public val offset: Long,
    ) : SeekFrom()

    /** Sets the offset to the size of this object plus the specified number of bytes. */
    public data class End(
        public val offset: Long,
    ) : SeekFrom()

    /** Sets the offset to the current position plus the specified number of bytes. */
    public data class Current(
        public val offset: Long,
    ) : SeekFrom()
}

/**
 * Read bytes asynchronously.
 *
 * This interface is analogous to standard blocking read operations, but integrates
 * with the asynchronous task system. In particular, [pollRead] will automatically queue
 * the current task for wakeup and return if data is not yet available, rather than
 * blocking the calling thread.
 */
@HiddenFromObjC
public interface AsyncRead {
    /**
     * Attempt to read from the [AsyncRead] into [buf].
     *
     * On success, returns [Poll.Ready] with [Try.Ok] containing the number of bytes read.
     * If no data is available for reading, returns [Poll.Pending] and arranges for the
     * current task to receive a notification when the object becomes readable or is closed.
     */
    public fun pollRead(
        context: TaskContext,
        buf: ByteArray,
        offset: Int = 0,
        length: Int = buf.size - offset,
    ): Poll<Try<Int, IoError>>
}

/**
 * Write bytes asynchronously.
 *
 * This interface is analogous to standard blocking write operations, but integrates
 * with the asynchronous task system. In particular, [pollWrite] will automatically queue
 * the current task for wakeup and return if the writer cannot take more data, rather than
 * blocking the calling thread.
 */
@HiddenFromObjC
public interface AsyncWrite {
    /**
     * Attempt to write bytes from [buf] into the object.
     *
     * On success, returns [Poll.Ready] with [Try.Ok] containing the number of bytes written.
     * If the object is not ready for writing, returns [Poll.Pending] and arranges for
     * the current task to receive a notification when the object becomes writable or is closed.
     */
    public fun pollWrite(
        context: TaskContext,
        buf: ByteArray,
        offset: Int = 0,
        length: Int = buf.size - offset,
    ): Poll<Try<Int, IoError>>

    /**
     * Attempt to flush the object, ensuring that any buffered data reaches its destination.
     */
    public fun pollFlush(context: TaskContext): Poll<Try<Unit, IoError>>

    /**
     * Attempt to close the object.
     */
    public fun pollClose(context: TaskContext): Poll<Try<Unit, IoError>>
}

/**
 * Seek bytes asynchronously.
 */
@HiddenFromObjC
public interface AsyncSeek {
    /**
     * Attempt to seek to an offset in a stream.
     */
    public fun pollSeek(
        context: TaskContext,
        pos: SeekFrom,
    ): Poll<Try<Long, IoError>>
}

/**
 * Read bytes asynchronously from an in-memory or buffered source.
 */
@HiddenFromObjC
public interface AsyncBufRead : AsyncRead {
    /**
     * Attempt to return the contents of the internal buffer, filling it with more data
     * from the inner reader if it is empty.
     */
    public fun pollFillBuf(context: TaskContext): Poll<Try<ByteArray, IoError>>

    /**
     * Informs this buffer that [amt] bytes have been consumed from the buffer.
     */
    public fun consume(amt: Int)
}
