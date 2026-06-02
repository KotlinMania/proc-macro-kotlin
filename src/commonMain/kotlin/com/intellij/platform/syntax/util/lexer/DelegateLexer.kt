// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.lexer

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.Lexer

open class DelegateLexer(
    val delegate: Lexer,
) : LexerBase() {
    override fun start(
        buffer: CharSequence,
        startOffset: Int,
        endOffset: Int,
        initialState: Int,
    ) {
        delegate.start(buffer, startOffset, endOffset, initialState)
    }

    override fun getState(): Int = delegate.getState()

    override fun getTokenType(): SyntaxElementType? = delegate.getTokenType()

    override fun getTokenStart(): Int = delegate.getTokenStart()

    override fun getTokenEnd(): Int = delegate.getTokenEnd()

    override fun advance() {
        delegate.advance()
    }

    override fun getBufferSequence(): CharSequence = delegate.getBufferSequence()

    override fun getBufferEnd(): Int = delegate.getBufferEnd()
}
