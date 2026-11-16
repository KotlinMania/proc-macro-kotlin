/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// Vendored from JetBrains/kotlin compiler/multiplatform-parsing/common/src/org/jetbrains/kotlin/kmp/lexer/KDocLexer.kt
package io.github.kotlinmania.procmacro.vendored.kotlin.kmp.lexer

import io.github.kotlinmania.procmacro.vendored.annotations.ApiStatus
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.SyntaxElementType
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.SyntaxElementTypeSet
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.element.SyntaxTokenTypes
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.lexer.Lexer
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.syntaxElementTypeSetOf
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.util.lexer.FlexAdapter
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.util.lexer.MergingLexerAdapter

@ApiStatus.Experimental
class KDocLexer :
    MergingLexerAdapter(
        FlexAdapter(KDocFlexLexer()),
        KDOC_TOKENS,
    ) {
    companion object {
        val KDOC_TOKENS: SyntaxElementTypeSet =
            syntaxElementTypeSetOf(
                KDocTokens.TEXT,
                KDocTokens.CODE_BLOCK_TEXT,
                SyntaxTokenTypes.WHITE_SPACE,
            )
    }

    override fun merge(
        tokenType: SyntaxElementType,
        lexer: Lexer,
    ): SyntaxElementType {
        val nextTokenType = lexer.getTokenType()
        val nextTokenText = lexer.getTokenText()
        if (
            tokenType == KDocTokens.CODE_BLOCK_TEXT &&
            nextTokenType == KDocTokens.TEXT &&
            (nextTokenText == "```" || nextTokenText == "~~~")
        ) {
            lexer.advance()
            return KDocTokens.TEXT
        } else if (
            tokenType == KDocTokens.CODE_BLOCK_TEXT ||
            tokenType == KDocTokens.TEXT ||
            tokenType == KtTokens.WHITE_SPACE
        ) {
            while (tokenType == lexer.getTokenType()) {
                lexer.advance()
            }
        }

        return tokenType
    }
}
