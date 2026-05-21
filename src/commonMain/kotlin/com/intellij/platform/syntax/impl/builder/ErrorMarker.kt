// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.element.SyntaxTokenTypes

internal class ErrorMarker(
  markerId: Int,
  builder: SyntaxTreeBuilderImpl,
) : ProductionMarker(markerId, builder) {

  private var errorMessage: String? = null

  override fun isErrorMarker(): Boolean = true

  override fun dispose() {
    super.dispose()
    errorMessage = null
  }

  override fun getErrorMessage(): String? {
    return errorMessage
  }

  fun setErrorMessage(value: String) {
    errorMessage = builder.errorInterner.intern(value)
  }

  override fun getEndOffset(): Int = getStartOffset()

  override fun getNodeType(): SyntaxElementType = SyntaxTokenTypes.ERROR_ELEMENT

  override fun getEndTokenIndex(): Int = startIndex

  override fun getLexemeIndex(done: Boolean): Int = startIndex

  override fun setLexemeIndex(value: Int, done: Boolean) =
    if (done) throw UnsupportedOperationException() else startIndex = value
}