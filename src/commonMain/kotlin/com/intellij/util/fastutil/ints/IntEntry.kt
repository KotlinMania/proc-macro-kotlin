// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.fastutil.ints

internal data class IntEntry<T>(
    var key: Int,
    val value: T,
)
