/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// Vendored from JetBrains/kotlin compiler/multiplatform-parsing/common/src/org/jetbrains/kotlin/kmp/parser/KDocParseNodes.kt
package io.github.kotlinmania.procmacro.vendored.kotlin.kmp.parser

import io.github.kotlinmania.procmacro.vendored.annotations.ApiStatus
import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.SyntaxElementType

@ApiStatus.Experimental
object KDocParseNodes {
    val KDOC_SECTION: SyntaxElementType = SyntaxElementType("KDOC_SECTION")
    val KDOC_TAG: SyntaxElementType = SyntaxElementType("KDOC_TAG")
    val KDOC_NAME: SyntaxElementType = SyntaxElementType("KDOC_NAME")
}
