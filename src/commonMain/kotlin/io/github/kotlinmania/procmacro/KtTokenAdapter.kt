// port-lint: source N/A (Kotlin-side adapter: no upstream Rust counterpart)
package io.github.kotlinmania.procmacro

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.Lexer
import org.jetbrains.kotlin.kmp.lexer.KtTokens

/**
 * Adapts a JetBrains [Lexer] (producing [SyntaxElementType] tokens from
 * the Kotlin-language tokenizer) into a [TokenStream] of `proc_macro`-
 * shaped [TokenTree] variants.
 *
 * The adapter runs the lexer end-to-end, skips whitespace and comments,
 * accumulates string-template tokens into atomic [Literal]s, decomposes
 * multi-character Kotlin operators into [Punct] sequences with correct
 * [Spacing], and matches delimiter pairs into nested [Group]s.
 */
internal object KtTokenAdapter {

    /**
     * Tokenizes [source] via the supplied [lexer] and returns a
     * [TokenStream] of `proc_macro`-shaped token trees.
     *
     * Returns [Result.failure] with [LexErrorThrown] on unbalanced
     * delimiters or unrecognized tokens.
     */
    fun tokenize(lexer: Lexer, source: CharSequence): Result<TokenStream> {
        lexer.start(source, 0, source.length, 0)

        // Collect all tokens from the lexer
        val raw = mutableListOf<RawToken>()
        while (lexer.getTokenType() != null) {
            raw.add(RawToken(lexer.getTokenType()!!, lexer.getTokenStart(), lexer.getTokenEnd()))
            lexer.advance()
        }

        val src: (Int, Int) -> String = { s, e -> source.subSequence(s, e).toString() }

        // Phase 1: filter whitespace and comments
        val noWhite = raw.filter { t ->
            !KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET.contains(t.type)
        }

        // Phase 2: collapse string-template token runs into single entries
        val collapsed = collapseStringTemplates(noWhite)

        // Phase 3: group delimiters into nested structure
        val grouped = groupDelimiters(collapsed)
            ?: return Result.failure(
                LexErrorThrown(LexError("Unbalanced delimiters in source")),
            )

        // Phase 4: convert flat tokens to TokenTree lists
        val trees = mutableListOf<TokenTree>()
        for (ft in grouped) {
            val converted = convertFlatToken(ft, src)
                ?: return Result.failure(
                    LexErrorThrown(
                        LexError("Unrecognized token at offset ${ft.start}: ${src(ft.start, ft.end)}"),
                    ),
                )
            trees.addAll(converted)
        }

        return Result.success(TokenStream(TokenStreamData(trees)))
    }

    // --- Internal representations ---

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

    // --- Phase 2: string template collapse ---

    /**
     * KotlinLexer breaks string literals into template parts
     * (OPEN_QUOTE, REGULAR_STRING_PART, ESCAPE_SEQUENCE,
     * SHORT/LONG_TEMPLATE_ENTRY_*, CLOSING_QUOTE, etc.).
     *
     * For `proc_macro` purposes a non-interpolated string is an atomic
     * [Literal]. String interpolation is a Kotlin-specific semantic
     * with no direct proc_macro decomposition — the entire template
     * (including `${...}` interpolations) is preserved as the literal's
     * source representation so that `toString()` round-trips correctly.
     */
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
                val closeEnd = if (i < tokens.size) {
                    i++ // consume CLOSING_QUOTE
                    tokens[i - 1].end
                } else {
                    first.end
                }
                result.add(FlatToken(
                    type = STRING_LITERAL,
                    start = first.start,
                    end = closeEnd,
                ))
            } else {
                result.add(FlatToken(type = t.type, start = t.start, end = t.end))
                i++
            }
        }
        return result
    }

    private fun isStringStart(type: SyntaxElementType): Boolean =
        type == KtTokens.OPEN_QUOTE || type == KtTokens.INTERPOLATION_PREFIX

    /** Sentinel type marking a collapsed string literal. */
    private val STRING_LITERAL = SyntaxElementType("KOTLIN_STRING_LITERAL", transient = true)

    // --- Phase 3: delimiter grouping ---

    /**
     * Walks the flat token list, matching LPAR/RPAR, LBRACE/RBRACE,
     * LBRACKET/RBRACKET into nested [FlatToken]s with children.
     * Returns null on unbalanced delimiters.
     */
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
                result.add(FlatToken(
                    type = openDelim,
                    start = t.start,
                    end = closeEnd,
                    children = innerGrouped,
                ))
            } else if (closeDelimiterOf(t.type) != null) {
                return null
            } else {
                result.add(t)
                i++
            }
        }
        return result
    }

    private fun openDelimiterOf(type: SyntaxElementType): SyntaxElementType? = when (type) {
        KtTokens.LPAR -> KtTokens.LPAR
        KtTokens.LBRACE -> KtTokens.LBRACE
        KtTokens.LBRACKET -> KtTokens.LBRACKET
        else -> null
    }

    private fun closeDelimiterOf(type: SyntaxElementType): SyntaxElementType? = when (type) {
        KtTokens.RPAR -> KtTokens.RPAR
        KtTokens.RBRACE -> KtTokens.RBRACE
        KtTokens.RBRACKET -> KtTokens.RBRACKET
        else -> null
    }

    // --- Phase 4: flat token to TokenTree conversion ---

    private fun convertFlatToken(
        ft: FlatToken,
        src: (Int, Int) -> String,
    ): List<TokenTree>? {
        val text = src(ft.start, ft.end)

        // Delimiter groups
        if (ft.children != null) {
            val delim = when (ft.type) {
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
            return listOf(TokenTree.Group(
                Group.new(delim, TokenStream(TokenStreamData(childTrees))),
            ))
        }

        // Collapsed string literal
        if (ft.type == STRING_LITERAL) {
            return listOf(TokenTree.Literal(Literal.fromKotlinString(text)))
        }

        // Character literal
        if (ft.type == KtTokens.CHARACTER_LITERAL) {
            return listOf(TokenTree.Literal(Literal.fromKotlinChar(text)))
        }

        // Integer literal
        if (ft.type == KtTokens.INTEGER_LITERAL) {
            return listOf(TokenTree.Literal(Literal.fromKotlinInteger(text)))
        }

        // Float literal
        if (ft.type == KtTokens.FLOAT_LITERAL) {
            return listOf(TokenTree.Literal(Literal.fromKotlinFloat(text)))
        }

        // Identifier and all keywords
        if (isIdentLike(ft.type)) {
            return listOf(TokenTree.Ident(Ident.new(text, Span.callSite())))
        }

        // Single-character punctuation
        val single = singleCharPunct(ft.type)
        if (single != null) {
            return listOf(TokenTree.Punct(Punct.new(single, Spacing.ALONE)))
        }

        // Multi-character operators decompose into Punct chain
        val multi = multiCharPunct(ft.type)
        if (multi != null) {
            return multi.mapIndexed { i, ch ->
                val spacing = if (i < multi.length - 1) Spacing.JOINT else Spacing.ALONE
                TokenTree.Punct(Punct.new(ch, spacing))
            }
        }

        // Compound tokens: NOT_IN (!in), NOT_IS (!is), AS_SAFE (as?)
        // These are atomic lexer tokens but decompose into Punct + Ident
        // or Ident + Punct in proc_macro token trees.
        val compound = compoundTokenDecomposition(ft.type, text)
        if (compound != null) return compound

        return null
    }

    /**
     * Whether the lexer token should map to [Ident]. In `proc_macro`,
     * keywords are identifiers — `class`, `fun`, `val` are all valid
     * idents in the token stream.
     */
    private fun isIdentLike(type: SyntaxElementType): Boolean =
        type == KtTokens.IDENTIFIER ||
            type == KtTokens.FIELD_IDENTIFIER ||
            KtTokens.HARD_KEYWORDS_AND_MODIFIERS.contains(type) ||
            KtTokens.SOFT_KEYWORDS_AND_MODIFIERS.contains(type)

    private fun singleCharPunct(type: SyntaxElementType): Char? = when (type) {
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

    private fun multiCharPunct(type: SyntaxElementType): String? = when (type) {
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
        KtTokens.SAFE_ACCESS -> "?."
        KtTokens.ELVIS -> "?:"
        KtTokens.RESERVED -> "..."
        else -> null
    }

    /**
     * Decomposes compound lexer tokens that blend punctuation and
     * identifier parts into proc_macro token sequences.
     *
     * - `NOT_IN` (`!in`) → `Punct('!', ALONE)` + `Ident("in")`
     * - `NOT_IS` (`!is`) → `Punct('!', ALONE)` + `Ident("is")`
     * - `AS_SAFE` (`as?`) → `Ident("as")` + `Punct('?', ALONE)`
     */
    private fun compoundTokenDecomposition(
        type: SyntaxElementType,
        text: String,
    ): List<TokenTree>? = when (type) {
        KtTokens.NOT_IN -> listOf(
            TokenTree.Punct(Punct.new('!', Spacing.ALONE)),
            TokenTree.Ident(Ident.new("in", Span.callSite())),
        )
        KtTokens.NOT_IS -> listOf(
            TokenTree.Punct(Punct.new('!', Spacing.ALONE)),
            TokenTree.Ident(Ident.new("is", Span.callSite())),
        )
        KtTokens.AS_SAFE -> listOf(
            TokenTree.Ident(Ident.new("as", Span.callSite())),
            TokenTree.Punct(Punct.new('?', Spacing.ALONE)),
        )
        else -> null
    }
}
