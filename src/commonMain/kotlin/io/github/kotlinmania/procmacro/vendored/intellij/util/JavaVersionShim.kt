// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.kotlinmania.procmacro.vendored.intellij.util

import io.github.kotlinmania.procmacro.vendored.intellij.util.lang.JavaVersion

/**
 * see implementation [io.github.kotlinmania.procmacro.vendored.intellij.util.currentJavaVersionPlatformSpecificJvm], [io.github.kotlinmania.procmacro.vendored.intellij.util.currentJavaVersionPlatformSpecificWasmJs]
 */
internal fun currentJavaVersionPlatformSpecific(): JavaVersion = DefaultJavaVersion

internal val DefaultJavaVersion: JavaVersion = JavaVersion.compose(21, 0, 0, 0, false)
