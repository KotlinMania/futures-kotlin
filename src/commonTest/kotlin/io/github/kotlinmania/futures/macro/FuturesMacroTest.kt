// port-lint: tests futures-macro/src/lib.rs
package io.github.kotlinmania.futures.macro

import io.github.kotlinmania.procmacro2.TokenStream
import kotlin.test.Test
import kotlin.test.assertTrue

class FuturesMacroTest {
    @Test
    fun joinMacroExpandsFutureBindingsAndPollFn() {
        val input = TokenStream.fromString("fut_a, fut_b()").getOrThrow()
        val expanded = joinInternal(input).toString()
        assertTrue(expanded.contains("maybe_done"))
        assertTrue(expanded.contains("_fut0"))
        assertTrue(expanded.contains("_fut1"))
        assertTrue(expanded.contains("poll_fn"))
    }

    @Test
    fun tryJoinMacroExpandsErrorHandlingAndPollFn() {
        val input = TokenStream.fromString("fut_a, fut_b()").getOrThrow()
        val expanded = tryJoinInternal(input).toString()
        assertTrue(expanded.contains("maybe_done"))
        assertTrue(expanded.contains("is_err"))
        assertTrue(expanded.contains("_fut0"))
        assertTrue(expanded.contains("_fut1"))
    }

    @Test
    fun selectAndSelectBiasedExpandResultEnumAndBranches() {
        val input =
            TokenStream.fromString(
                "a = fut_a => a + 1, b = create_fut() => { b }, complete => 0, default => -1",
            ).getOrThrow()
        val randomExpanded = selectInternal(input).toString()
        assertTrue(randomExpanded.contains("__PrivResult"))
        assertTrue(randomExpanded.contains("assert_fused_future"))
        assertTrue(randomExpanded.contains("shuffle"))
        assertTrue(randomExpanded.contains("Complete"))

        val biasedInput =
            TokenStream.fromString(
                "a = fut_a => a + 1, complete => 0",
            ).getOrThrow()
        val biasedExpanded = selectBiasedInternal(biasedInput).toString()
        assertTrue(biasedExpanded.contains("__PrivResult"))
        assertTrue(!biasedExpanded.contains("shuffle"))
    }

    @Test
    fun testAttributeMacroWrapsAsyncFunctionWithBlockOn() {
        val args = TokenStream.new()
        val item = TokenStream.fromString("async fn my_async_test() { let x = 1; assert_eq!(x, 1); }").getOrThrow()
        val expanded = testInternal(args, item).toString()
        assertTrue(expanded.contains("block_on"))
        assertTrue(expanded.contains("my_async_test"))

        val syncItem = TokenStream.fromString("fn sync_fn() {}").getOrThrow()
        val syncError = testInternal(TokenStream.new(), syncItem).toString()
        assertTrue(syncError.contains("compile_error"))
    }

    @Test
    fun streamSelectMacroExpandsAndRejectsSingleArg() {
        val valid = TokenStream.fromString("s1, s2").getOrThrow()
        val expanded = streamSelectInternal(valid).toString()
        assertTrue(expanded.contains("StreamSelect"))
        assertTrue(expanded.contains("StreamEnum"))

        val single = TokenStream.fromString("s1").getOrThrow()
        val errorExpanded = streamSelectInternal(single).toString()
        assertTrue(errorExpanded.contains("compile_error"))
    }
}
