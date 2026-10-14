// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.kotlinmania.procmacro.vendored.intellij.openapi.util.text

/**
 * This interface implementations must have `hashCode` values equal to those for String.
 *
 * @see io.github.kotlinmania.procmacro.vendored.intellij.openapi.util.text.StringUtil.stringHashCode
 */

interface CharSequenceWithStringHash : CharSequence {
    override fun hashCode(): Int
}
