// port-lint: source futures-macro/src/executor.rs
package io.github.kotlinmania.futures.macro

import io.github.kotlinmania.procmacro2.Span
import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.quote.quote
import io.github.kotlinmania.quote.quoteSpanned
import io.github.kotlinmania.syn.Item
import io.github.kotlinmania.syn.ParseMacro2SynResult
import io.github.kotlinmania.syn.Stmt
import io.github.kotlinmania.syn.SynError
import io.github.kotlinmania.syn.SynResult
import io.github.kotlinmania.syn.parse2
import io.github.kotlinmania.syn.parseExpr
import io.github.kotlinmania.syn.parseMacroInput2

internal fun test(args: TokenStream, item: TokenStream): TokenStream {
    if (!args.isEmpty()) {
        return SynError.newSpanned(args, "invalid argument").toCompileError()
    }

    val input =
        when (
            val parsed =
                parseMacroInput2(item) { stream ->
                    val parsedItem = Item.parse(stream).getOrElse { return@parseMacroInput2 SynResult.failure(it) }
                    val itemFn =
                        parsedItem as? Item.Fn
                            ?: return@parseMacroInput2 SynResult.failure(stream.error("expected function"))
                    SynResult.success(itemFn)
                }
        ) {
            is ParseMacro2SynResult.Success -> parsed.value
            is ParseMacro2SynResult.CompileError -> return parsed.tokens
        }

    val asyncness = input.sig.asyncness
    input.sig.asyncness = null
    if (asyncness == null) {
        return SynError.newSpanned(input.sig.fnToken, "Only async functions are supported").toCompileError()
    }

    val body = input.block

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
