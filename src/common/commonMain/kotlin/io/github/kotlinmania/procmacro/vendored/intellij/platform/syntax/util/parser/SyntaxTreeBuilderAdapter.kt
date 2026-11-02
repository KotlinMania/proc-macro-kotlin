// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.util.parser

import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.parser.SyntaxTreeBuilder

open class SyntaxTreeBuilderAdapter(
    private val delegate: SyntaxTreeBuilder,
) : SyntaxTreeBuilder by delegate
