// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions

/**
 * A key that allows to get extensions registered under the corresponding [name].
 */
internal class ExtensionPointKey internal constructor(
    val name: String,
    _constructorToken: ExtensionPointKeyConstructorToken,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is ExtensionPointKey && name == other.name)

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = "ExtensionKey($name)"
}

internal fun ExtensionPointKey(name: String): ExtensionPointKey = ExtensionPointKey(name, ExtensionPointKeyConstructorToken)

internal object ExtensionPointKeyConstructorToken
