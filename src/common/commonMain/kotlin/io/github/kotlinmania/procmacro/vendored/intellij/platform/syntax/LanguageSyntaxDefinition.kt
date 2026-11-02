// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax

import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.element.SyntaxTokenTypes
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.lexer.Lexer
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser.OpaqueElementPolicy
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser.SyntaxTreeBuilder
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser.WhitespaceOrCommentBindingPolicy

/**
 * Extension point providing syntax implementation for a given [io.github.kotlinmania.procmacro.vendored.intellij.lang.Language]
 *
 * @see io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.psi.LanguageSyntaxDefinitions
 */

interface LanguageSyntaxDefinition {
    fun parse(builder: SyntaxTreeBuilder)

    /**
     * An instance of [io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.lexer.Lexer] for the language with default settings.
     * Create your lexer directly if you need to set some custom settings.
     */
    fun createLexer(): Lexer

    /**
     * The set of comment token types of the language
     */
    val comments: SyntaxElementTypeSet

    /**
     * The set of whitespace token types of the language
     */
    val whitespaces: SyntaxElementTypeSet get() = syntaxElementTypeSetOf(SyntaxTokenTypes.WHITE_SPACE)

    /**
     * Controls whitespace balancing behavior of [io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser.SyntaxTreeBuilder].
     * For more details see [io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser.WhitespaceOrCommentBindingPolicy]
     */
    val whitespaceOrCommentBindingPolicy: WhitespaceOrCommentBindingPolicy?
        get() = null

    /**
     * The policy for handling opaque elements in the syntax tree.
     * For more details see [io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser.OpaqueElementPolicy]
     */
    val opaqueElementPolicy: OpaqueElementPolicy?
        get() = null
}
