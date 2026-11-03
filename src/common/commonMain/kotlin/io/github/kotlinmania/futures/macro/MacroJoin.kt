// port-lint: source futures-macro/src/join.rs
package io.github.kotlinmania.futures.macro

import io.github.kotlinmania.procmacro2.Ident
import io.github.kotlinmania.procmacro2.Span
import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.quote.formatIdent
import io.github.kotlinmania.quote.quote
import io.github.kotlinmania.syn.CommaParse
import io.github.kotlinmania.syn.Expr
import io.github.kotlinmania.syn.ParseMacroSynResult
import io.github.kotlinmania.syn.ParseStream
import io.github.kotlinmania.syn.SynResult
import io.github.kotlinmania.syn.parseExpr
import io.github.kotlinmania.syn.parseMacroInput

internal class Join(
    val futExprs: MutableList<Expr> = mutableListOf(),
) {
    companion object {
        fun parse(input: ParseStream): SynResult<Join> {
            val join = Join()

            while (!input.isEmpty()) {
                join.futExprs.add(parseExpr(input).getOrElse { return SynResult.failure(it) })

                if (!input.isEmpty()) {
                    CommaParse.parse(input).getOrElse { return SynResult.failure(it) }
                }
            }

            return SynResult.success(join)
        }
    }
}

internal fun bindFutures(futExprs: List<Expr>, span: Span): Pair<List<TokenStream>, List<Ident>> {
    val futureLetBindings = ArrayList<TokenStream>(futExprs.size)
    val futureNames =
        futExprs.mapIndexed { i, expr ->
            val name = formatIdent("_fut{}", i).also { it.setSpan(span) }
            futureLetBindings.add(
                quote(
                    """
                    let mut `#`name = __futures_crate::future::maybe_done(`#`expr);
                    let mut `#`name = unsafe { __futures_crate::Pin::new_unchecked(&mut `#`name) };
                    """.trimIndent(),
                    "name" to name,
                    "expr" to expr,
                ),
            )
            name
        }

    return futureLetBindings to futureNames
}

/**
 * Procedural macro implementation for `join`.
 */
internal fun join(input: TokenStream): TokenStream {
    val parsed =
        when (val result = parseMacroInput(input) { Join.parse(it) }) {
            is ParseMacroSynResult.Success -> result.value
            is ParseMacroSynResult.CompileError -> return result.tokens
        }

    val span = Span.callSite()
    val (futureLetBindings, futureNames) = bindFutures(parsed.futExprs, span)

    val pollFutures =
        futureNames.map { fut ->
            quote(
                """
                __all_done &= __futures_crate::future::Future::poll(
                    `#`fut.as_mut(), __cx).is_ready();
                """.trimIndent(),
                "fut" to fut,
            )
        }
    val takeOutputs =
        futureNames.map { fut ->
            quote(
                "`#`fut.as_mut().take_output().unwrap(),",
                "fut" to fut,
            )
        }

    return quote(
        """
        {
            `#`( `#`future_let_bindings )*

            __futures_crate::future::poll_fn(move |__cx: &mut __futures_crate::task::Context<'_>| {
                let mut __all_done = true;
                `#`( `#`poll_futures )*
                if __all_done {
                    __futures_crate::task::Poll::Ready((
                        `#`( `#`take_outputs )*
                    ))
                } else {
                    __futures_crate::task::Poll::Pending
                }
            }).await
        }
        """.trimIndent(),
        "future_let_bindings" to futureLetBindings,
        "poll_futures" to pollFutures,
        "take_outputs" to takeOutputs,
    )
}

/**
 * Procedural macro implementation for `tryJoin`.
 */
internal fun tryJoin(input: TokenStream): TokenStream {
    val parsed =
        when (val result = parseMacroInput(input) { Join.parse(it) }) {
            is ParseMacroSynResult.Success -> result.value
            is ParseMacroSynResult.CompileError -> return result.tokens
        }

    val span = Span.callSite()
    val (futureLetBindings, futureNames) = bindFutures(parsed.futExprs, span)

    val pollFutures =
        futureNames.map { fut ->
            quote(
                """
                if __futures_crate::future::Future::poll(
                    `#`fut.as_mut(), __cx).is_pending()
                {
                    __all_done = false;
                } else if `#`fut.as_mut().output_mut().unwrap().is_err() {
                    #[allow(unreachable_code)]
                    return __futures_crate::task::Poll::Ready(
                        __futures_crate::Err(
                            `#`fut.as_mut().take_output().unwrap().err().unwrap()
                        )
                    );
                }
                """.trimIndent(),
                "fut" to fut,
            )
        }
    val takeOutputs =
        futureNames.map { fut ->
            quote(
                """
                #[allow(unreachable_code)]
                `#`fut.as_mut().take_output().unwrap().ok().unwrap(),
                """.trimIndent(),
                "fut" to fut,
            )
        }

    return quote(
        """
        {
            `#`( `#`future_let_bindings )*

            #[allow(clippy::diverging_sub_expression)]
            __futures_crate::future::poll_fn(move |__cx: &mut __futures_crate::task::Context<'_>| {
                let mut __all_done = true;
                `#`( `#`poll_futures )*
                if __all_done {
                    __futures_crate::task::Poll::Ready(
                        __futures_crate::Ok((
                            `#`( `#`take_outputs )*
                        ))
                    )
                } else {
                    __futures_crate::task::Poll::Pending
                }
            }).await
        }
        """.trimIndent(),
        "future_let_bindings" to futureLetBindings,
        "poll_futures" to pollFutures,
        "take_outputs" to takeOutputs,
    )
}
