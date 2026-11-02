// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.util.lexer

import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.SyntaxElementType
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.SyntaxElementTypeSet
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.lexer.Lexer

open class MergingLexerAdapter(
    delegate: Lexer,
    private val tokenSet: SyntaxElementTypeSet,
) : MergingLexerAdapterBase(delegate) {
    override fun merge(
        tokenType: SyntaxElementType,
        lexer: Lexer,
    ): SyntaxElementType {
        if (!tokenSet.contains(tokenType)) {
            return tokenType
        }

        while (true) {
            val token = lexer.getTokenType()
            if (token !== tokenType) break
            lexer.advance()
        }
        return tokenType
    }
}
