// port-lint: source futures-macro/src/stream_select.rs
package io.github.kotlinmania.futures.macro

import io.github.kotlinmania.procmacro2.Span
import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.quote.formatIdent
import io.github.kotlinmania.quote.quote
import io.github.kotlinmania.syn.CommaParse
import io.github.kotlinmania.syn.ExprList
import io.github.kotlinmania.syn.Index
import io.github.kotlinmania.syn.SynResult
import io.github.kotlinmania.syn.parse2
import io.github.kotlinmania.syn.parseExpr

/**
 * Procedural macro implementation for `streamSelect`.
 */
internal fun streamSelect(input: TokenStream): SynResult<TokenStream> {
    val args =
        parse2(
            { stream ->
                val list = ExprList()
                while (!stream.isEmpty()) {
                    list.pushValue(parseExpr(stream).getOrElse { return@parse2 SynResult.failure(it) })
                    if (stream.isEmpty()) break
                    list.pushPunct(CommaParse.parse(stream).getOrElse { return@parse2 SynResult.failure(it) })
                }
                SynResult.success(list)
            },
            input,
        ).getOrElse { return SynResult.failure(it) }
    if (args.len() < 2) {
        return SynResult.success(
            quote("compile_error!(\"stream select macro needs at least two arguments.\")"),
        )
    }

    val genericIdents = (0 until args.len()).map { i -> formatIdent("_{}", i) }
    val fieldIdents = (0 until args.len()).map { i -> formatIdent("__{}", i) }
    val fieldIdents2 = (0 until args.len()).map { i -> formatIdent("___{}", i) }
    val fieldIndices = (0 until args.len()).map { i -> Index(i.toUInt(), Span.callSite()) }
    val argTokens = args.toList().map { e -> e.toTokenStream() }

    val tokens =
        quote(
            """
            {
                #[derive(Debug)]
                struct StreamSelect<`#`(`#`generic_idents),*> (`#`(Option<`#`generic_idents>),*);

                enum StreamEnum<`#`(`#`generic_idents),*> {
                    `#`(
                        `#`generic_idents(`#`generic_idents)
                    ),*,
                    None,
                }

                impl<ITEM, `#`(`#`generic_idents),*> __futures_crate::stream::Stream for StreamEnum<`#`(`#`generic_idents),*>
                where `#`(`#`generic_idents: __futures_crate::stream::Stream<Item=ITEM> + ::std::marker::Unpin,)*
                {
                    type Item = ITEM;

                    fn poll_next(mut self: ::std::pin::Pin<&mut Self>, cx: &mut __futures_crate::task::Context<'_>) -> __futures_crate::task::Poll<Option<Self::Item>> {
                        match self.get_mut() {
                            `#`(
                                Self::`#`generic_idents(`#`generic_idents) => ::std::pin::Pin::new(`#`generic_idents).poll_next(cx)
                            ),*,
                            Self::None => panic!("StreamEnum::None should never be polled!"),
                        }
                    }
                }

                impl<ITEM, `#`(`#`generic_idents),*> __futures_crate::stream::Stream for StreamSelect<`#`(`#`generic_idents),*>
                where `#`(`#`generic_idents: __futures_crate::stream::Stream<Item=ITEM> + ::std::marker::Unpin,)*
                {
                    type Item = ITEM;

                    fn poll_next(mut self: ::std::pin::Pin<&mut Self>, cx: &mut __futures_crate::task::Context<'_>) -> __futures_crate::task::Poll<Option<Self::Item>> {
                        let Self(`#`(ref mut `#`field_idents),*) = self.get_mut();
                        `#`(
                            let mut `#`field_idents_2 = false;
                        )*
                        let mut any_pending = false;
                        {
                            let mut stream_array = [`#`(`#`field_idents.as_mut().map(|f| StreamEnum::`#`generic_idents(f)).unwrap_or(StreamEnum::None)),*];
                            __futures_crate::async_await::shuffle(&mut stream_array);

                            for mut s in stream_array {
                                if let StreamEnum::None = s {
                                    continue;
                                } else {
                                    match __futures_crate::stream::Stream::poll_next(::std::pin::Pin::new(&mut s), cx) {
                                        r @ __futures_crate::task::Poll::Ready(Some(_)) => {
                                            return r;
                                        },
                                        __futures_crate::task::Poll::Pending => {
                                            any_pending = true;
                                        },
                                        __futures_crate::task::Poll::Ready(None) => {
                                            match s {
                                                `#`(
                                                    StreamEnum::`#`generic_idents(_) => { `#`field_idents_2 = true; }
                                                ),*,
                                                StreamEnum::None => panic!("StreamEnum::None should never be polled!"),
                                            }
                                        },
                                    }
                                }
                            }
                        }
                        `#`(
                            if `#`field_idents_2 {
                                *`#`field_idents = None;
                            }
                        )*
                        if any_pending {
                            __futures_crate::task::Poll::Pending
                        } else {
                            __futures_crate::task::Poll::Ready(None)
                        }
                    }

                    fn size_hint(&self) -> (usize, Option<usize>) {
                        let mut s = (0, Some(0));
                        `#`(
                            if let Some(new_hint) = self.`#`field_indices.as_ref().map(|s| s.size_hint()) {
                                s.0 += new_hint.0;
                                s.1 = s.1 .and_then(|a| new_hint.1 .map(|b| a + b));
                            }
                        )*
                        s
                    }
                }

                StreamSelect(`#`(Some(`#`args)),*)
            }
            """.trimIndent(),
            "generic_idents" to genericIdents,
            "field_idents" to fieldIdents,
            "field_idents_2" to fieldIdents2,
            "field_indices" to fieldIndices,
            "args" to argTokens,
        )

    return SynResult.success(tokens)
}
