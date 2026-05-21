// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.parser

import com.intellij.platform.syntax.SyntaxElementType

/**
 * This policy allows overriding the text of an element type
 *
 * @link [com.intellij.lang.TokenWrapper] class
 */

fun interface OpaqueElementPolicy {
  /**
   * @return text of opaque element type
   */
  fun getTextOfOpaqueElement(elementType: SyntaxElementType): String?
}