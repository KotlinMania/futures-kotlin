// port-lint: source futures-macro/src/select.rs
package io.github.kotlinmania.futures.macro

import io.github.kotlinmania.procmacro2.Ident
import io.github.kotlinmania.procmacro2.Span
import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.quote.formatIdent
import io.github.kotlinmania.quote.quote
import io.github.kotlinmania.syn.CommaParse
import io.github.kotlinmania.syn.CommaPeek
import io.github.kotlinmania.syn.DefaultPeek
import io.github.kotlinmania.syn.EqParse
import io.github.kotlinmania.syn.Expr
import io.github.kotlinmania.syn.FatArrowParse
import io.github.kotlinmania.syn.IdentParse
import io.github.kotlinmania.syn.Item
import io.github.kotlinmania.syn.ParseMacroSynResult
import io.github.kotlinmania.syn.ParseStream
import io.github.kotlinmania.syn.Pat
import io.github.kotlinmania.syn.Path
import io.github.kotlinmania.syn.SynResult
import io.github.kotlinmania.syn.customKeyword
import io.github.kotlinmania.syn.parse2
import io.github.kotlinmania.syn.parseExpr
import io.github.kotlinmania.syn.parseMacroInput
import io.github.kotlinmania.syn.parseWithEarlierBoundaryRule

private val completeKeyword = customKeyword("complete")

internal class Select(
    var complete: Expr? = null,
    var default: Expr? = null,
    val normalFutExprs: MutableList<Expr> = mutableListOf(),
    val normalFutHandlers: MutableList<Pair<Pat, Expr>> = mutableListOf(),
) {
    companion object {
        fun parse(input: ParseStream): SynResult<Select> {
            val select = Select()

            while (!input.isEmpty()) {
                val caseKind =
                    if (input.peek(completeKeyword.first)) {
                        if (select.complete != null) {
                            return SynResult.failure(input.error("multiple `complete` cases found, only one allowed"))
                        }
                        completeKeyword.second.parse(input).getOrElse { return SynResult.failure(it) }
                        CaseKind.Complete
                    } else if (input.peek(DefaultPeek)) {
                        if (select.default != null) {
                            return SynResult.failure(input.error("multiple `default` cases found, only one allowed"))
                        }
                        IdentParse.parse(input).getOrElse { return SynResult.failure(it) }
                        CaseKind.Default
                    } else {
                        val pat = Pat.parseMultiWithLeadingVert(input).getOrElse { return SynResult.failure(it) }
                        EqParse.parse(input).getOrElse { return SynResult.failure(it) }
                        val expr = parseExpr(input).getOrElse { return SynResult.failure(it) }
                        CaseKind.Normal(pat, expr)
                    }

                FatArrowParse.parse(input).getOrElse { return SynResult.failure(it) }
                val expr = parseWithEarlierBoundaryRule(input).getOrElse { return SynResult.failure(it) }

                val isBlock = expr is Expr.BlockExpr
                if (isBlock || input.isEmpty()) {
                    if (input.peek(CommaPeek)) {
                        CommaParse.parse(input).getOrElse { return SynResult.failure(it) }
                    }
                } else {
                    CommaParse.parse(input).getOrElse { return SynResult.failure(it) }
                }

                when (caseKind) {
                    is CaseKind.Complete -> select.complete = expr
                    is CaseKind.Default -> select.default = expr
                    is CaseKind.Normal -> {
                        select.normalFutExprs.add(caseKind.expr)
                        select.normalFutHandlers.add(caseKind.pat to expr)
                    }
                }
            }

            return SynResult.success(select)
        }
    }
}

internal sealed class CaseKind {
    data object Complete : CaseKind()

    data object Default : CaseKind()

    data class Normal(
        val pat: Pat,
        val expr: Expr,
    ) : CaseKind()
}

internal fun declareResultEnum(
    resultIdent: Ident,
    variants: Int,
    complete: Boolean,
    span: Span,
): Pair<List<Ident>, Item.Enum> {
    val variantNames =
        (0 until variants).map { num ->
            formatIdent("_{}", num).also { it.setSpan(span) }
        }

    val completeVariant = if (complete) quote("Complete") else null

    val enumTokens =
        quote(
            """
            enum `#`result_ident<`#`(`#`type_parameters,)*> {
                `#`(
                    `#`variants(`#`type_parameters),
                )*
                `#`complete_variant
            }
            """.trimIndent(),
            "result_ident" to resultIdent,
            "type_parameters" to variantNames,
            "variants" to variantNames,
            "complete_variant" to completeVariant,
        )

    val enumItem = parse2({ Item.parse(it) }, enumTokens).getOrThrow() as Item.Enum
    return variantNames to enumItem
}

/**
 * Procedural macro implementation for `select`.
 */
internal fun select(input: TokenStream): TokenStream = selectInner(input, true)

/**
 * Procedural macro implementation for `selectBiased`.
 */
internal fun selectBiased(input: TokenStream): TokenStream = selectInner(input, false)

internal fun selectInner(input: TokenStream, random: Boolean): TokenStream {
    val parsed =
        when (val result = parseMacroInput(input) { Select.parse(it) }) {
            is ParseMacroSynResult.Success -> result.value
            is ParseMacroSynResult.CompileError -> return result.tokens
        }

    val span = Span.callSite()
    val enumIdent = Ident.new("__PrivResult", span)

    val (variantNames, enumItem) =
        declareResultEnum(
            enumIdent,
            parsed.normalFutExprs.size,
            parsed.complete != null,
            span,
        )

    val futureLetBindings = ArrayList<TokenStream>(parsed.normalFutExprs.size)
    val boundFutureNames =
        parsed.normalFutExprs.zip(variantNames).map { (expr, variantName) ->
            when (expr) {
                is Expr.Path -> {
                    futureLetBindings.add(
                        quote(
                            """
                            __futures_crate::async_await::assert_fused_future(&`#`path);
                            __futures_crate::async_await::assert_unpin(&`#`path);
                            """.trimIndent(),
                            "path" to expr,
                        ),
                    )
                    expr
                }
                else -> {
                    futureLetBindings.add(
                        quote(
                            "let mut `#`variant_name = `#`expr;",
                            "variant_name" to variantName,
                            "expr" to expr,
                        ),
                    )
                    Expr.Path(emptyList(), null, Path.from(variantName))
                }
            }
        }

    val pollFunctions =
        boundFutureNames.zip(variantNames).map { (boundFutureName, variantName) ->
            quote(
                """
                let mut `#`variant_name = |__cx: &mut __futures_crate::task::Context<'_>| {
                    let mut `#`bound_future_name = unsafe {
                        __futures_crate::Pin::new_unchecked(&mut `#`bound_future_name)
                    };
                    if __futures_crate::future::FusedFuture::is_terminated(&`#`bound_future_name) {
                        __futures_crate::None
                    } else {
                        __futures_crate::Some(__futures_crate::future::FutureExt::poll_unpin(
                            &mut `#`bound_future_name,
                            __cx,
                        ).map(`#`enum_ident::`#`variant_name))
                    }
                };
                let `#`variant_name: &mut dyn FnMut(
                    &mut __futures_crate::task::Context<'_>
                ) -> __futures_crate::Option<__futures_crate::task::Poll<_>> = &mut `#`variant_name;
                """.trimIndent(),
                "bound_future_name" to boundFutureName,
                "variant_name" to variantName,
                "enum_ident" to enumIdent,
            )
        }

    val nonePolled =
        if (parsed.complete != null) {
            quote(
                "__futures_crate::task::Poll::Ready(`#`enum_ident::Complete)",
                "enum_ident" to enumIdent,
            )
        } else {
            quote(
                "panic!(\"all futures in select! were completed, but no `complete =>` handler was provided\")",
            )
        }

    val normalBranchTokens =
        parsed.normalFutHandlers.zip(variantNames).map { (handler, variantName) ->
            val (pat, expr) = handler
            quote(
                "`#`enum_ident::`#`variant_name(`#`pat) => `#`expr,",
                "enum_ident" to enumIdent,
                "variant_name" to variantName,
                "pat" to pat,
                "expr" to expr,
            )
        }
    val completeBranch =
        parsed.complete?.let { completeExpr ->
            quote(
                "`#`enum_ident::Complete => { `#`complete_expr },",
                "enum_ident" to enumIdent,
                "complete_expr" to completeExpr,
            )
        }

    val branches =
        quote(
            """
            `#`( `#`normal_branches )*
            `#`complete_branch
            """.trimIndent(),
            "normal_branches" to normalBranchTokens,
            "complete_branch" to completeBranch,
        )

    val awaitSelectFut =
        if (parsed.default != null) {
            quote(
                """
                __poll_fn(&mut __futures_crate::task::Context::from_waker(
                    __futures_crate::task::noop_waker_ref()
                ))
                """.trimIndent(),
            )
        } else {
            quote("__futures_crate::future::poll_fn(__poll_fn).await")
        }

    val executeResultExpr =
        parsed.default?.let { defaultExpr ->
            quote(
                """
                match __select_result {
                    __futures_crate::task::Poll::Ready(result) => match result {
                        `#`branches
                    },
                    _ => `#`default_expr
                }
                """.trimIndent(),
                "branches" to branches,
                "default_expr" to defaultExpr,
            )
        } ?: quote(
            """
            match __select_result {
                `#`branches
            }
            """.trimIndent(),
            "branches" to branches,
        )

    val shuffle =
        if (random) {
            quote("__futures_crate::async_await::shuffle(&mut __select_arr);")
        } else {
            quote("")
        }

    return quote(
        """
        {
            `#`enum_item

            let __select_result = {
                `#`( `#`future_let_bindings )*

                let mut __poll_fn = |__cx: &mut __futures_crate::task::Context<'_>| {
                    let mut __any_polled = false;

                    `#`( `#`poll_functions )*

                    let mut __select_arr = [`#`( `#`variant_names ),*];
                    `#`shuffle
                    for poller in &mut __select_arr {
                        let poller: &mut &mut dyn FnMut(
                            &mut __futures_crate::task::Context<'_>
                        ) -> __futures_crate::Option<__futures_crate::task::Poll<_>> = poller;
                        match poller(__cx) {
                            __futures_crate::Some(x @ __futures_crate::task::Poll::Ready(_)) =>
                                return x,
                            __futures_crate::Some(__futures_crate::task::Poll::Pending) => {
                                __any_polled = true;
                            }
                            __futures_crate::None => {}
                        }
                    }

                    if !__any_polled {
                        `#`none_polled
                    } else {
                        __futures_crate::task::Poll::Pending
                    }
                };

                `#`await_select_fut
            };

            `#`execute_result_expr
        }
        """.trimIndent(),
        "enum_item" to enumItem,
        "future_let_bindings" to futureLetBindings,
        "poll_functions" to pollFunctions,
        "variant_names" to variantNames,
        "shuffle" to shuffle,
        "none_polled" to nonePolled,
        "await_select_fut" to awaitSelectFut,
        "execute_result_expr" to executeResultExpr,
    )
}
