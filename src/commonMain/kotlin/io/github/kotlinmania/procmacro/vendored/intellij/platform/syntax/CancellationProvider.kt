// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax

import kotlin.coroutines.cancellation.CancellationException

/**
 * Cancellation Provider allows stopping a task if it is cancelled by external factors.
 *
 * @See io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser.SyntaxTreeBuilderFactory.Builder.withCancellationProvider
 */
interface CancellationProvider {
    @Throws(CancellationException::class)
    fun checkCancelled()
}
