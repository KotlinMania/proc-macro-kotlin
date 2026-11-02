// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser

import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.SyntaxElementType

/**
 * This policy allows overriding the text of an element type
 *
 * @link [io.github.kotlinmania.procmacro.vendored.intellij.lang.TokenWrapper] class
 */

interface OpaqueElementPolicy {
    /**
     * @return text of opaque element type
     */
    fun getTextOfOpaqueElement(elementType: SyntaxElementType): String?
}

object NoOpaqueElementPolicy : OpaqueElementPolicy {
    override fun getTextOfOpaqueElement(elementType: SyntaxElementType): String? = null
}
