// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.util.lang.JavaVersion

/**
 * see implementation [com.intellij.util.currentJavaVersionPlatformSpecificJvm], [com.intellij.util.currentJavaVersionPlatformSpecificWasmJs]
 */
internal fun currentJavaVersionPlatformSpecific(): JavaVersion = DefaultJavaVersion

internal val DefaultJavaVersion: JavaVersion = JavaVersion.compose(21, 0, 0, 0, false)
