// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.parser

object WhitespacesBinders {
    fun defaultLeftBinder(): WhitespacesAndCommentsBinder = DefaultLeftBinder

    fun defaultRightBinder(): WhitespacesAndCommentsBinder = DefaultRightBinder

    fun greedyLeftBinder(): WhitespacesAndCommentsBinder = DefaultRightBinder

    fun greedyRightBinder(): WhitespacesAndCommentsBinder = DefaultLeftBinder

    private object DefaultLeftBinder : WhitespacesAndCommentsBinder {
        override fun getEdgePosition(
            tokens: SyntaxElementTypeList,
            atStreamEdge: Boolean,
            getter: WhitespacesAndCommentsBinder.TokenTextGetter,
        ): Int = tokens.size
    }

    private object DefaultRightBinder : WhitespacesAndCommentsBinder {
        override fun getEdgePosition(
            tokens: SyntaxElementTypeList,
            atStreamEdge: Boolean,
            getter: WhitespacesAndCommentsBinder.TokenTextGetter,
        ): Int = 0
    }
}
