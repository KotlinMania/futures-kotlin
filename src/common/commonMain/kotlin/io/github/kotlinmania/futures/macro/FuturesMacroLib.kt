@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.macro

import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.syn.SynResult
import kotlin.native.HiddenFromObjC

/**
 * Procedural macro entry point for `join`.
 */
@HiddenFromObjC
public fun joinInternal(input: TokenStream): TokenStream = join(input)

/**
 * Procedural macro entry point for `tryJoin`.
 */
@HiddenFromObjC
public fun tryJoinInternal(input: TokenStream): TokenStream = tryJoin(input)

/**
 * Procedural macro entry point for `select`.
 */
@HiddenFromObjC
public fun selectInternal(input: TokenStream): TokenStream = select(input)

/**
 * Procedural macro entry point for `selectBiased`.
 */
@HiddenFromObjC
public fun selectBiasedInternal(input: TokenStream): TokenStream = selectBiased(input)

/**
 * Procedural macro attribute entry point for `test`.
 */
@HiddenFromObjC
public fun testInternal(input: TokenStream, item: TokenStream): TokenStream = test(input, item)

/**
 * Procedural macro entry point for `streamSelect`.
 */
@HiddenFromObjC
public fun streamSelectInternal(input: TokenStream): TokenStream =
    when (val result = streamSelect(input)) {
        is SynResult.Success -> result.value
        is SynResult.Failure -> result.error.intoCompileError()
    }
