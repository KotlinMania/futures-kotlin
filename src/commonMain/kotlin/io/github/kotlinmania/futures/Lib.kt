// port-lint: source lib.rs
package io.github.kotlinmania.futures

/**
 * Abstractions for asynchronous programming.
 *
 * This library provides a number of core abstractions for writing asynchronous code:
 *
 * - **Futures**: single eventual values produced by asynchronous computations.
 *   Some programming environments call this concept a deferred value or promise.
 * - **Streams**: represent a series of values produced asynchronously over time.
 * - **Sinks**: provide support for asynchronous writing of data.
 * - **Tasks and Executors**: responsible for managing and driving asynchronous computation.
 *
 * The library also contains abstractions for asynchronous I/O and cross-task communication
 * including channels, locks, and synchronization primitives.
 *
 * Underlying all of this is the task system, which is a form of lightweight cooperative
 * scheduling. Large asynchronous computations are built up using futures, streams, and sinks,
 * and executed to completion without blocking the underlying thread.
 *
 * ### Common Abstractions
 *
 * - [Future]: An asynchronous computation that eventually produces a single value.
 * - [Stream]: An asynchronous sequence of elements received over time.
 * - [Sink]: A destination that accepts elements asynchronously.
 */
private const val MODULE_LEDGER = true
