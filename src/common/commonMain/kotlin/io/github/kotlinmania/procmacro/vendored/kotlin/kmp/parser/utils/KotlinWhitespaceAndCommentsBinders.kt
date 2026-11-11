/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// Vendored from JetBrains/kotlin compiler/multiplatform-parsing/common/src/org/jetbrains/kotlin/kmp/parser/utils/KotlinWhitespaceAndCommentsBinders.kt
package io.github.kotlinmania.procmacro.vendored.kotlin.kmp.parser.utils

import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser.SyntaxElementTypeList
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser.WhitespacesAndCommentsBinder
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.lexer.KtTokens
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.utils.StringUtil

internal object PrecedingCommentsBinder : WhitespacesAndCommentsBinder {
    override fun getEdgePosition(
        tokens: SyntaxElementTypeList,
        atStreamEdge: Boolean,
        getter: WhitespacesAndCommentsBinder.TokenTextGetter,
    ): Int {
        if (tokens.size == 0) return 0

        // 1. bind doc comment
        for (idx in (tokens.size - 1 downTo 0)) {
            if (tokens.get(idx) == KtTokens.DOC_COMMENT) return idx
        }

        // 2. bind plain comments
        var result = tokens.size
        tokens@ for (idx in (tokens.size - 1 downTo 0)) {
            val tokenType = tokens.get(idx)
            when (tokenType) {
                KtTokens.WHITE_SPACE -> if (StringUtil.getLineBreakCount(getter.get(idx)) > 1) break@tokens

                in KtTokens.COMMENTS -> {
                    if (idx == 0 || (tokens.get(idx - 1) == KtTokens.WHITE_SPACE && StringUtil.containsLineBreak(getter.get(idx - 1)))) {
                        result = idx
                    }
                }

                else -> break@tokens
            }
        }

        return result
    }
}

internal object PrecedingDocCommentsBinder : WhitespacesAndCommentsBinder {
    override fun getEdgePosition(
        tokens: SyntaxElementTypeList,
        atStreamEdge: Boolean,
        getter: WhitespacesAndCommentsBinder.TokenTextGetter,
    ): Int {
        if (tokens.size == 0) return 0

        for (idx in (tokens.size - 1 downTo 0)) {
            if (tokens.get(idx) == KtTokens.DOC_COMMENT) return idx
        }

        return tokens.size
    }
}

// Binds comments on the same line
internal object TrailingCommentsBinder : WhitespacesAndCommentsBinder {
    override fun getEdgePosition(
        tokens: SyntaxElementTypeList,
        atStreamEdge: Boolean,
        getter: WhitespacesAndCommentsBinder.TokenTextGetter,
    ): Int {
        if (tokens.size == 0) return 0

        var result = 0
        tokens@ for (idx in (0 until tokens.size)) {
            val tokenType = tokens.get(idx)
            when (tokenType) {
                KtTokens.WHITE_SPACE -> if (StringUtil.containsLineBreak(getter.get(idx))) break@tokens

                KtTokens.EOL_COMMENT, KtTokens.BLOCK_COMMENT -> result = idx + 1

                else -> break@tokens
            }
        }

        return result
    }
}

private class AllCommentsBinder(
    val isTrailing: Boolean,
) : WhitespacesAndCommentsBinder {
    override fun getEdgePosition(
        tokens: SyntaxElementTypeList,
        atStreamEdge: Boolean,
        getter: WhitespacesAndCommentsBinder.TokenTextGetter,
    ): Int {
        if (tokens.size == 0) return 0

        val size = tokens.size

        // Skip one whitespace if needed. Expect that there can't be several consecutive whitespaces
        val endToken = tokens.get(if (isTrailing) size - 1 else 0)
        val shift = if (endToken == KtTokens.WHITE_SPACE) 1 else 0

        return if (isTrailing) size - shift else shift
    }
}

internal val PRECEDING_ALL_COMMENTS_BINDER: WhitespacesAndCommentsBinder = AllCommentsBinder(isTrailing = false)

internal val TRAILING_ALL_COMMENTS_BINDER: WhitespacesAndCommentsBinder = AllCommentsBinder(isTrailing = true)

internal object DoNotBindAnything : WhitespacesAndCommentsBinder {
    override fun getEdgePosition(
        tokens: SyntaxElementTypeList,
        atStreamEdge: Boolean,
        getter: WhitespacesAndCommentsBinder.TokenTextGetter,
    ): Int = 0
}

internal object BindFirstShebangWithWhitespaceOnly : WhitespacesAndCommentsBinder {
    override fun getEdgePosition(
        tokens: SyntaxElementTypeList,
        atStreamEdge: Boolean,
        getter: WhitespacesAndCommentsBinder.TokenTextGetter,
    ): Int {
        if (tokens.size > 0 && tokens.get(0) == KtTokens.SHEBANG_COMMENT) {
            return if (tokens.size > 1 && tokens.get(1) == KtTokens.WHITE_SPACE) 2 else 1
        }

        return 0
    }
}

internal class BindAll(
    val isTrailing: Boolean,
) : WhitespacesAndCommentsBinder {
    override fun getEdgePosition(
        tokens: SyntaxElementTypeList,
        atStreamEdge: Boolean,
        getter: WhitespacesAndCommentsBinder.TokenTextGetter,
    ): Int = if (!isTrailing) 0 else tokens.size
}

internal val PRECEDING_ALL_BINDER: WhitespacesAndCommentsBinder = BindAll(isTrailing = false)

internal val TRAILING_ALL_BINDER: WhitespacesAndCommentsBinder = BindAll(isTrailing = true)
