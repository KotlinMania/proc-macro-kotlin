// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.extensions.impl

import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.extensions.ExtensionRegistry

/**
 * Simple [ExtensionSupport] backend that is used outside IntelliJ runtime.
 * Threadsafe.
 */
internal class ExtensionRegistryImpl : ExtensionRegistry
