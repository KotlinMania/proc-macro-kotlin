// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.parser

import com.intellij.platform.syntax.SyntaxElementType

/**
 * A hook allowing watch skipped whitespaces
 *
 * @See SyntaxTreeBuilder.setWhitespaceSkippedCallback
 */

interface WhitespaceSkippedCallback {
  fun onSkip(type: SyntaxElementType, start: Int, end: Int)
}
