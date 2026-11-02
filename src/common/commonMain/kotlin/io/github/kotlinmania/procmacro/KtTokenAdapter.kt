package io.github.kotlinmania.procmacro

import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.SyntaxElementType
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.lexer.Lexer
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.lexer.KtTokens

/**
 * Adapts a multiplatform Kotlin [Lexer] into a [TokenStream] of [TokenTree]
 * variants.
 *
 * Runs the lexer end-to-end, skips whitespace and comments, collapses
 * string-template runs into atomic [Literal] tokens, decomposes
 * multi-character operators into [Punct] chains with [Spacing], and pairs
 * balanced delimiters into nested [Group] values.
 */
internal object KtTokenAdapter {
    fun tokenize(
        lexer: Lexer,
        source: CharSequence,
    ): TokenStreamParseOutcome {
        lexer.start(source, 0, source.length, 0)

        val raw = mutableListOf<RawToken>()
        while (lexer.getTokenType() != null) {
            raw.add(RawToken(lexer.getTokenType()!!, lexer.getTokenStart(), lexer.getTokenEnd()))
            lexer.advance()
        }

        val src: (Int, Int) -> String = { s, e -> source.subSequence(s, e).toString() }

        val noWhite =
            raw.filter { t ->
                !KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET.contains(t.type)
            }

        val collapsed = collapseStringTemplates(noWhite)

        val grouped =
            groupDelimiters(collapsed)
                ?: return TokenStreamParseOutcome.Err(LexError("Unbalanced delimiters in source"))

        val trees = mutableListOf<TokenTree>()
        for (ft in grouped) {
            val converted =
                convertFlatToken(ft, src)
                    ?: return TokenStreamParseOutcome.Err(
                        LexError("Unrecognized token at offset ${ft.start}: ${src(ft.start, ft.end)}"),
                    )
            trees.addAll(converted)
        }

        return TokenStreamParseOutcome.Ok(TokenStream(TokenStreamData(trees)))
    }

    private class RawToken(
        val type: SyntaxElementType,
        val start: Int,
        val end: Int,
    )

    private class FlatToken(
        val type: SyntaxElementType,
        val start: Int,
        val end: Int,
        val children: List<FlatToken>? = null,
    )

    private fun collapseStringTemplates(tokens: List<RawToken>): List<FlatToken> {
        val result = mutableListOf<FlatToken>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (isStringStart(t.type)) {
                val first = t
                i++
                while (i < tokens.size && tokens[i].type != KtTokens.CLOSING_QUOTE) {
                    i++
                }
                val closeEnd =
                    if (i < tokens.size) {
                        i++
                        tokens[i - 1].end
                    } else {
                        first.end
                    }
                result.add(
                    FlatToken(
                        type = stringLiteralElement,
                        start = first.start,
                        end = closeEnd,
                    ),
                )
            } else {
                result.add(FlatToken(type = t.type, start = t.start, end = t.end))
                i++
            }
        }
        return result
    }

    private fun isStringStart(type: SyntaxElementType): Boolean =
        type == KtTokens.OPEN_QUOTE || type == KtTokens.INTERPOLATION_PREFIX

    private val stringLiteralElement = SyntaxElementType("KOTLIN_STRING_LITERAL", transient = true)

    private fun groupDelimiters(tokens: List<FlatToken>): List<FlatToken>? {
        val result = mutableListOf<FlatToken>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val openDelim = openDelimiterOf(t.type)
            if (openDelim != null) {
                val childList = mutableListOf<FlatToken>()
                i++
                var depth = 1
                while (i < tokens.size && depth > 0) {
                    val inner = tokens[i]
                    if (openDelimiterOf(inner.type) != null) depth++
                    if (closeDelimiterOf(inner.type) != null) depth--
                    if (depth > 0) childList.add(inner)
                    i++
                }
                if (depth != 0) return null
                val closeEnd = tokens[i - 1].end
                val innerGrouped = groupDelimiters(childList) ?: return null
                result.add(
                    FlatToken(
                        type = openDelim,
                        start = t.start,
                        end = closeEnd,
                        children = innerGrouped,
                    ),
                )
            } else if (closeDelimiterOf(t.type) != null) {
                return null
            } else {
                result.add(t)
                i++
            }
        }
        return result
    }

    private fun openDelimiterOf(type: SyntaxElementType): SyntaxElementType? =
        when (type) {
            KtTokens.LPAR -> KtTokens.LPAR
            KtTokens.LBRACE -> KtTokens.LBRACE
            KtTokens.LBRACKET -> KtTokens.LBRACKET
            else -> null
        }

    private fun closeDelimiterOf(type: SyntaxElementType): SyntaxElementType? =
        when (type) {
            KtTokens.RPAR -> KtTokens.RPAR
            KtTokens.RBRACE -> KtTokens.RBRACE
            KtTokens.RBRACKET -> KtTokens.RBRACKET
            else -> null
        }

    private fun convertFlatToken(
        ft: FlatToken,
        src: (Int, Int) -> String,
    ): List<TokenTree>? {
        val text = src(ft.start, ft.end)

        if (ft.children != null) {
            val delim =
                when (ft.type) {
                    KtTokens.LPAR -> Delimiter.PARENTHESIS
                    KtTokens.LBRACE -> Delimiter.BRACE
                    KtTokens.LBRACKET -> Delimiter.BRACKET
                    else -> return null
                }
            val childTrees = mutableListOf<TokenTree>()
            for (child in ft.children) {
                val converted = convertFlatToken(child, src) ?: return null
                childTrees.addAll(converted)
            }
            return listOf(
                TokenTree.Group(
                    Group.new(delim, TokenStream(TokenStreamData(childTrees))),
                ),
            )
        }

        if (ft.type == stringLiteralElement) {
            return listOf(TokenTree.Literal(Literal.fromKotlinString(text)))
        }

        if (ft.type == KtTokens.CHARACTER_LITERAL) {
            return listOf(TokenTree.Literal(Literal.fromKotlinChar(text)))
        }

        if (ft.type == KtTokens.INTEGER_LITERAL) {
            return listOf(TokenTree.Literal(Literal.fromKotlinInteger(text)))
        }

        if (ft.type == KtTokens.FLOAT_LITERAL) {
            return listOf(TokenTree.Literal(Literal.fromKotlinFloat(text)))
        }

        val compound = compoundTokenDecomposition(ft.type)
        if (compound != null) return compound

        if (isIdentLike(ft.type)) {
            return listOf(TokenTree.Ident(Ident.new(text, Span.callSite())))
        }

        val single = singleCharPunct(ft.type)
        if (single != null) {
            return listOf(TokenTree.Punct(Punct.new(single, Spacing.ALONE)))
        }

        val multi = multiCharPunct(ft.type)
        if (multi != null) {
            return multi.mapIndexed { i, ch ->
                val spacing = if (i < multi.length - 1) Spacing.JOINT else Spacing.ALONE
                TokenTree.Punct(Punct.new(ch, spacing))
            }
        }

        return null
    }

    private fun isIdentLike(type: SyntaxElementType): Boolean =
        type == KtTokens.IDENTIFIER ||
            type == KtTokens.FIELD_IDENTIFIER ||
            (KtTokens.HARD_KEYWORDS_AND_MODIFIERS.contains(type) && type !in compoundKeywordSet) ||
            KtTokens.SOFT_KEYWORDS_AND_MODIFIERS.contains(type)

    private val compoundKeywordSet: Set<SyntaxElementType> =
        setOf(
            KtTokens.NOT_IN,
            KtTokens.NOT_IS,
            KtTokens.AS_SAFE,
        )

    private fun singleCharPunct(type: SyntaxElementType): Char? =
        when (type) {
            KtTokens.PLUS -> '+'
            KtTokens.MINUS -> '-'
            KtTokens.MUL -> '*'
            KtTokens.DIV -> '/'
            KtTokens.PERC -> '%'
            KtTokens.LT -> '<'
            KtTokens.GT -> '>'
            KtTokens.EXCL -> '!'
            KtTokens.AND -> '&'
            KtTokens.EQ -> '='
            KtTokens.COLON -> ':'
            KtTokens.SEMICOLON -> ';'
            KtTokens.DOT -> '.'
            KtTokens.COMMA -> ','
            KtTokens.AT -> '@'
            KtTokens.HASH -> '#'
            KtTokens.QUEST -> '?'
            else -> null
        }

    private fun multiCharPunct(type: SyntaxElementType): String? =
        when (type) {
            KtTokens.ARROW -> "->"
            KtTokens.DOUBLE_ARROW -> "=>"
            KtTokens.PLUSPLUS -> "++"
            KtTokens.MINUSMINUS -> "--"
            KtTokens.LTEQ -> "<="
            KtTokens.GTEQ -> ">="
            KtTokens.EQEQ -> "=="
            KtTokens.EQEQEQ -> "==="
            KtTokens.EXCLEQ -> "!="
            KtTokens.EXCLEQEQEQ -> "!=="
            KtTokens.EXCLEXCL -> "!!"
            KtTokens.ANDAND -> "&&"
            KtTokens.OROR -> "||"
            KtTokens.MULTEQ -> "*="
            KtTokens.DIVEQ -> "/="
            KtTokens.PERCEQ -> "%="
            KtTokens.PLUSEQ -> "+="
            KtTokens.MINUSEQ -> "-="
            KtTokens.RANGE -> ".."
            KtTokens.RANGE_UNTIL -> "..<"
            KtTokens.COLONCOLON -> "::"
            KtTokens.DOUBLE_SEMICOLON -> ";;"
            KtTokens.RESERVED -> "..."
            else -> null
        }

    private fun compoundTokenDecomposition(type: SyntaxElementType): List<TokenTree>? =
        when (type) {
            KtTokens.NOT_IN ->
                listOf(
                    TokenTree.Punct(Punct.new('!', Spacing.ALONE)),
                    TokenTree.Ident(Ident.new("in", Span.callSite())),
                )
            KtTokens.NOT_IS ->
                listOf(
                    TokenTree.Punct(Punct.new('!', Spacing.ALONE)),
                    TokenTree.Ident(Ident.new("is", Span.callSite())),
                )
            KtTokens.AS_SAFE ->
                listOf(
                    TokenTree.Ident(Ident.new("as", Span.callSite())),
                    TokenTree.Punct(Punct.new('?', Spacing.ALONE)),
                )
            else -> null
        }
}
