// port-lint: source never.rs
package io.github.kotlinmania.futures

/**
 * A type with no possible values.
 *
 * This is used to indicate values which can never be created, such as the
 * error type of infallible futures.
 */
public typealias Never = Nothing
