/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// Vendored from JetBrains/kotlin compiler/multiplatform-parsing/jvm/test/org/jetbrains/kotlin/kmp/infra/AbstractTestLexer.kt
package io.github.kotlinmania.procmacro.vendored.kotlin.kmp.infra

abstract class AbstractTestLexer<T> {
    abstract fun tokenize(text: String): TestToken<T>

    protected fun List<TestToken<T>>.wrap(end: Int): TestToken<T> =
        TestToken(
            TestSyntaxElement.WRAPPER_SYNTAX_ELEMENT_NAME,
            0,
            end,
            token = null,
            this,
        )
}
