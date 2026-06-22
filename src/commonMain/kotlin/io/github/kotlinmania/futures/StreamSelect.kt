// port-lint: source futures-macro/src/stream_select.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.procmacro2.Delimiter
import io.github.kotlinmania.procmacro2.Group
import io.github.kotlinmania.procmacro2.Ident
import io.github.kotlinmania.procmacro2.Literal
import io.github.kotlinmania.procmacro2.Punct
import io.github.kotlinmania.procmacro2.Span
import io.github.kotlinmania.procmacro2.Spacing
import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.procmacro2.TokenTree
import io.github.kotlinmania.quote.ToTokens
import io.github.kotlinmania.quote.append
import io.github.kotlinmania.quote.toTokens
import io.github.kotlinmania.syn.SynError
import io.github.kotlinmania.syn.SynResult

internal fun streamSelect(input: TokenStream): SynResult<TokenStream> {
    val argStreams = splitTopLevelCommas(input)
    if (argStreams.size < 2) {
        return SynResult.success(compileErrorTokens("stream select macro needs at least two arguments."))
    }

    val count = argStreams.size
    val genericIdents = (0 until count).map { i -> Ident.new("_$i", Span.callSite()) }
    val tokens = TokenStream.new()

    tokens.append(Ident.new("struct", Span.callSite()))
    tokens.append(Ident.new("StreamSelect", Span.callSite()))
    punct(tokens, '<')
    appendSeparatedIdents(tokens, genericIdents)
    punct(tokens, '>')
    punct(tokens, '(')
    val optionWrappers = genericIdents.map { ident -> optionWrapper(ident) }
    appendSeparatedBoxed(tokens, optionWrappers)
    punct(tokens, ')')
    punct(tokens, ';')

    tokens.append(Ident.new("enum", Span.callSite()))
    tokens.append(Ident.new("StreamEnum", Span.callSite()))
    punct(tokens, '<')
    appendSeparatedIdents(tokens, genericIdents)
    punct(tokens, '>')
    punct(tokens, '{')
    for (ident in genericIdents) {
        tokens.append(ident)
        punct(tokens, '(')
        tokens.append(ident)
        punct(tokens, ')')
        punct(tokens, ',')
    }
    tokens.append(Ident.new("None", Span.callSite()))
    punct(tokens, ',')
    punct(tokens, '}')

    tokens.append(Ident.new("StreamSelect", Span.callSite()))
    punct(tokens, '(')
    val someWrappers = argStreams.map { stream -> someWrapper(stream) }
    appendSeparatedBoxed(tokens, someWrappers)
    punct(tokens, ')')

    return SynResult.success(tokens)
}

internal fun streamSelectInternal(input: TokenStream): TokenStream {
    val result = streamSelect(input)
    return when (result) {
        is SynResult.Success<TokenStream> -> result.value
        is SynResult.Failure<TokenStream> -> result.error.intoCompileError()
    }
}

private fun splitTopLevelCommas(stream: TokenStream): List<TokenStream> {
    val result = mutableListOf<TokenStream>()
    var current = TokenStream.new()
    for (token in stream) {
        if (token is TokenTree.Punct && token.value.asChar() == ',') {
            result.add(current)
            current = TokenStream.new()
        } else {
            current.extendTokenTrees(listOf(token))
        }
    }
    if (!current.isEmpty()) result.add(current)
    return result
}

private fun compileErrorTokens(message: String): TokenStream {
    val tokens = TokenStream.new()
    tokens.append(Ident.new("compile_error", Span.callSite()))
    punct(tokens, '!')
    val messageStream = TokenStream.new()
    messageStream.append(Literal.string(message))
    tokens.append(Group(Delimiter.Parenthesis, messageStream))
    return tokens
}

private fun punct(tokens: TokenStream, ch: Char) {
    tokens.append(Punct(ch, Spacing.Alone))
}

private fun optionWrapper(ident: Ident): BoxedTokens {
    val wrapper = TokenStream.new()
    wrapper.append(Ident.new("Option", Span.callSite()))
    punct(wrapper, '<')
    wrapper.append(ident)
    punct(wrapper, '>')
    return BoxedTokens(wrapper)
}

private fun someWrapper(stream: TokenStream): BoxedTokens {
    val wrapper = TokenStream.new()
    wrapper.append(Ident.new("Some", Span.callSite()))
    punct(wrapper, '(')
    wrapper.extendTokenStreams(listOf(stream))
    punct(wrapper, ')')
    return BoxedTokens(wrapper)
}

private fun appendSeparatedIdents(tokens: TokenStream, idents: List<Ident>) {
    var first = true
    for (ident in idents) {
        if (!first) punct(tokens, ',')
        first = false
        tokens.append(ident)
    }
}

private fun appendSeparatedBoxed(tokens: TokenStream, items: List<BoxedTokens>) {
    var first = true
    for (item in items) {
        if (!first) punct(tokens, ',')
        first = false
        item.toTokens(tokens)
    }
}

private class BoxedTokens(private val stream: TokenStream) : ToTokens {
    override fun toTokens(tokens: TokenStream) {
        tokens.extendTokenStreams(listOf(stream))
    }
}