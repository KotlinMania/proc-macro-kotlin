// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.util.lexer

import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.SyntaxElementType
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.SyntaxElementTypeSet
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.lexer.Lexer
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.lexer.LexerPosition

class FilterLexer constructor(
    original: Lexer,
    private val filter: Filter?,
    private val stateFilter: BooleanArray? = null,
) : DelegateLexer(original) {
    var prevTokenEnd: Int = 0
        private set

    interface Filter {
        fun reject(type: SyntaxElementType): Boolean
    }

    class SetFilter(
        private val set: SyntaxElementTypeSet,
    ) : Filter {
        override fun reject(type: SyntaxElementType): Boolean = type in set
    }

    val original: Lexer
        get() = delegate

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        super.start(buffer, startOffset, endOffset, initialState)
        prevTokenEnd = -1
        locateToken()
    }

    override fun advance() {
        prevTokenEnd = delegate.getTokenEnd()
        super.advance()
        locateToken()
    }

    override fun getCurrentPosition(): LexerPosition = delegate.getCurrentPosition()

    override fun restore(position: LexerPosition) {
        delegate.restore(position)
        this.prevTokenEnd = -1
    }

    fun locateToken() {
        while (true) {
            val delegate = delegate
            val tokenType = delegate.getTokenType() ?: break
            if (filter == null || !filter.reject(tokenType)) {
                if (stateFilter == null || !stateFilter[delegate.getState()]) {
                    break
                }
            }
            delegate.advance()
        }
    }
}
