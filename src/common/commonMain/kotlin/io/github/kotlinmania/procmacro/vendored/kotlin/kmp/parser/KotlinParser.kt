/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// Vendored from JetBrains/kotlin compiler/multiplatform-parsing/common/src/org/jetbrains/kotlin/kmp/parser/KotlinParser.kt
package io.github.kotlinmania.procmacro.vendored.kotlin.kmp.parser

import io.github.kotlinmania.procmacro.vendored.annotations.ApiStatus
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.SyntaxElementType
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.element.SyntaxTokenTypes
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser.SyntaxTreeBuilder
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser.WhitespaceOrCommentBindingPolicy
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.lexer.KtTokens
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.parser.utils.KotlinParsing
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.parser.utils.SemanticWhitespaceAwareSyntaxBuilderImpl

@ApiStatus.Experimental
class KotlinParser(
    val isScript: Boolean,
    val isLazy: Boolean,
) : AbstractParser() {
    override val whitespaces: Set<SyntaxElementType> = KtTokens.WHITESPACES
    override val comments: Set<SyntaxElementType> = KtTokens.COMMENTS

    override val whitespaceOrCommentBindingPolicy: WhitespaceOrCommentBindingPolicy =
        object : WhitespaceOrCommentBindingPolicy {
            override fun isLeftBound(elementType: SyntaxElementType): Boolean =
                elementType == SyntaxTokenTypes.ERROR_ELEMENT ||
                    elementType == KtNodeTypes.CONSTRUCTOR_DELEGATION_CALL ||
                    elementType == KtNodeTypes.CONSTRUCTOR_DELEGATION_REFERENCE
        }

    override fun parse(builder: SyntaxTreeBuilder) {
        val whitespaceAwareBuilder = SemanticWhitespaceAwareSyntaxBuilderImpl(builder)
        val kotlinParsing =
            if (isLazy) {
                KotlinParsing.createForTopLevel(whitespaceAwareBuilder)
            } else {
                KotlinParsing.createForTopLevelNonLazy(whitespaceAwareBuilder)
            }
        if (isScript) {
            kotlinParsing.parseScript()
        } else {
            kotlinParsing.parseFile()
        }
    }
}
