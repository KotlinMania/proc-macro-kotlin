/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// Vendored from JetBrains/kotlin compiler/multiplatform-parsing/common/src/org/jetbrains/kotlin/kmp/parser/KDocLinkParser.kt
package io.github.kotlinmania.procmacro.vendored.kotlin.kmp.parser

import io.github.kotlinmania.procmacro.vendored.annotations.ApiStatus
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.SyntaxElementType
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser.SyntaxTreeBuilder
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.lexer.KDocTokens
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.lexer.KtTokens

/**
 * Parses the contents of a Markdown link in KDoc. Uses the standard Kotlin lexer.
 */
@ApiStatus.Experimental
object KDocLinkParser : AbstractParser() {
    override val whitespaces: Set<SyntaxElementType> = KtTokens.WHITESPACES
    override val comments: Set<SyntaxElementType> = KtTokens.COMMENTS

    override fun parse(builder: SyntaxTreeBuilder) {
        val rootMarker = builder.mark()
        val hasLBracket = builder.tokenType == KtTokens.LBRACKET
        if (hasLBracket) {
            builder.advanceLexer()
        }
        parseQualifiedName(builder)
        if (hasLBracket) {
            if (!builder.eof() && builder.tokenType != KtTokens.RBRACKET) {
                builder.error("Closing bracket expected")
                while (!builder.eof() && builder.tokenType != KtTokens.RBRACKET) {
                    builder.advanceLexer()
                }
            }
            if (builder.tokenType == KtTokens.RBRACKET) {
                builder.advanceLexer()
            }
        } else {
            if (!builder.eof()) {
                builder.error("Expression expected")
                while (!builder.eof()) {
                    builder.advanceLexer()
                }
            }
        }
        rootMarker.done(KDocTokens.MARKDOWN_LINK)
    }

    private fun parseQualifiedName(builder: SyntaxTreeBuilder) {
        var marker = builder.mark()
        while (true) {
            if (!isName(builder.tokenType)) {
                marker.drop()
                builder.error("Identifier expected")
                break
            }
            builder.advanceLexer()
            marker.done(KDocParseNodes.KDOC_NAME)
            if (builder.tokenType != KtTokens.DOT) {
                break
            }
            marker = marker.precede()
            builder.advanceLexer()
        }
    }

    private fun isName(tokenType: SyntaxElementType?): Boolean =
        tokenType == KtTokens.IDENTIFIER || tokenType in KtTokens.HARD_KEYWORDS_AND_MODIFIERS
}
