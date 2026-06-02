// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.element

import com.intellij.platform.syntax.SyntaxElementType

/**
 * A set of most used basic token types
 */
object SyntaxTokenTypes {
  /**
   * Token type for a sequence of whitespace characters.
   */
  val WHITE_SPACE: SyntaxElementType = SyntaxElementType("WHITE_SPACE")

  /**
   * Token type for a character which is not valid in the position where it was encountered,
   * according to the language grammar.
   */
  val BAD_CHARACTER: SyntaxElementType = SyntaxElementType("BAD_CHARACTER")

  val ERROR_ELEMENT: SyntaxElementType = SyntaxElementType("ERROR_ELEMENT")
}
