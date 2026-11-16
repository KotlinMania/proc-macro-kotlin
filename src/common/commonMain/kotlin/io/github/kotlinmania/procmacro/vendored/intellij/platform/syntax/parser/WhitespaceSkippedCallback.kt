// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser

import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.SyntaxElementType

/**
 * A hook allowing watch skipped whitespaces
 *
 * @See SyntaxTreeBuilder.setWhitespaceSkippedCallback
 */

interface WhitespaceSkippedCallback {
    fun onSkip(
        type: SyntaxElementType,
        start: Int,
        end: Int,
    )
}
