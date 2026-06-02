// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions

import com.intellij.platform.syntax.extensions.impl.buildExtensionSupportImpl
import com.intellij.platform.syntax.extensions.impl.performWithExtensionSupportImpl
import com.intellij.platform.syntax.extensions.impl.registry
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Provides the current instance of [ExtensionSupport].
 */
internal fun currentExtensionSupport(): ExtensionSupport = registry

/**
 * Provides the current instance of [ExtensionSupport] or `null` if it is not supported in the current environment (i.e., in IntelliJ runtime).
 */
internal fun currentExtensionRegistry(): ExtensionRegistry = registry

/**
 * Provides access for extensions registered in the current container.
 *
 * Two extension point kinds are supported: plain Extension Points and Language Extension Points.
 *
 * Works both inside and outside of IntelliJ environment.
 * When working inside IJ environment, extensions are picked up from the IJ plugin model.
 * When working outside of IJ environment, extensions must be registered explicitly.
 *
 * @see com.intellij.platform.syntax.SyntaxLanguage
 * @see ExtensionRegistry
 * @see ExtensionPointKey
 * @see performWithExtensionSupport
 * @see buildExtensionSupport
 */
internal interface ExtensionSupport

/**
 * Allows registering extensions for [ExtensionSupport].
 * It is not supported in IntelliJ runtime. IJ plugin model is used instead.
 */
internal interface ExtensionRegistry : ExtensionSupport

/**
 * Marker interface for extension support that does not support dynamic substitution
 */
internal interface StaticExtensionSupport : ExtensionRegistry

/**
 * Runs [action] with [support] installed as the current instance of [ExtensionSupport].
 * The previous instance is restored on method exit.
 */
@OptIn(ExperimentalContracts::class)
internal fun performWithExtensionSupport(
    support: ExtensionRegistry,
    action: (ExtensionRegistry) -> Unit,
) {
    contract {
        callsInPlace(action, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    performWithExtensionSupportImpl(support, action)
}

/**
 * Builds [ExtensionSupport] instance.
 * It is not installed as the current instance of [ExtensionSupport].
 */
internal fun buildExtensionSupport(block: ExtensionRegistry.() -> Unit): ExtensionRegistry =
    buildExtensionSupportImpl(block)
