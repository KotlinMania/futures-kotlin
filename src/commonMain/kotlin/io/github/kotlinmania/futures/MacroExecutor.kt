// port-lint: source futures-macro/src/executor.rs
package io.github.kotlinmania.futures.macro

import io.github.kotlinmania.procmacro2.Span
import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.quote.quote
import io.github.kotlinmania.quote.quoteSpanned
import io.github.kotlinmania.syn.Item
import io.github.kotlinmania.syn.ParseMacroSynResult
import io.github.kotlinmania.syn.Stmt
import io.github.kotlinmania.syn.SynError
import io.github.kotlinmania.syn.SynResult
import io.github.kotlinmania.syn.parse2
import io.github.kotlinmania.syn.parseExpr
import io.github.kotlinmania.syn.parseMacroInput

internal fun test(args: TokenStream, item: TokenStream): TokenStream {
    if (!args.isEmpty()) {
        return SynError.newSpanned(args, "invalid argument").toCompileError()
    }

    val input =
        when (
            val parsed =
                parseMacroInput(item) { stream ->
                    val parsedItem = Item.parse(stream).getOrElse { return@parseMacroInput SynResult.failure(it) }
                    val itemFn =
                        parsedItem as? Item.Fn
                            ?: return@parseMacroInput SynResult.failure(stream.error("expected function"))
                    SynResult.success(itemFn)
                }
        ) {
            is ParseMacroSynResult.Success -> parsed.value
            is ParseMacroSynResult.CompileError -> return parsed.tokens
        }

    val asyncness = input.sig.asyncness
    input.sig.asyncness = null
    if (asyncness == null) {
        return SynError.newSpanned(input.sig.fnToken, "Only async functions are supported").toCompileError()
    }

    val body =
        input.block
            ?: return SynError.newSpanned(input.sig.fnToken, "expected function body").toCompileError()

    val (lastStmtStartSpan, lastStmtEndSpan) =
        run {
            val tokens =
                body.stmts
                    .lastOrNull()
                    ?.intoTokenStream()
                    ?.toList()
                    .orEmpty()
            val start = tokens.firstOrNull()?.span() ?: Span.callSite()
            val end = tokens.lastOrNull()?.span() ?: start
            start to end
        }

    val path =
        quoteSpanned(
            lastStmtStartSpan,
            "::futures_test::__private",
        )
    val blockOnExpr =
        parse2(
            { parseExpr(it) },
            quoteSpanned(
                lastStmtEndSpan,
                "`#`path::block_on(async `#`body)",
                "path" to path,
                "body" to body,
            ),
        ).getOrThrow()

    input.block = body.copy(stmts = listOf(Stmt.ExprStmt(blockOnExpr, null)))

    return quote(
        """
        #[::core::prelude::v1::test]
        `#`input
        """.trimIndent(),
        "input" to input,
    )
}
