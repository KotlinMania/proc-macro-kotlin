/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// Vendored from JetBrains/kotlin compiler/multiplatform-parsing/common/src/org/jetbrains/kotlin/kmp/parser/AbstractParser.kt
package io.github.kotlinmania.procmacro.vendored.kotlin.kmp.parser

import io.github.kotlinmania.procmacro.vendored.annotations.ApiStatus
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.SyntaxElementType
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.element.SyntaxTokenTypes
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser.SyntaxTreeBuilder
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser.WhitespaceOrCommentBindingPolicy

@ApiStatus.Experimental
abstract class AbstractParser {
    abstract fun parse(builder: SyntaxTreeBuilder)

    abstract val whitespaces: Set<SyntaxElementType>

    abstract val comments: Set<SyntaxElementType>

    open val whitespaceOrCommentBindingPolicy: WhitespaceOrCommentBindingPolicy =
        object : WhitespaceOrCommentBindingPolicy {
            override fun isLeftBound(elementType: SyntaxElementType): Boolean = elementType == SyntaxTokenTypes.ERROR_ELEMENT
        }
}
