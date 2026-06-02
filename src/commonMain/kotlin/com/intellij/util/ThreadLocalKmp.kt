// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import fleet.util.multiplatform.linkToActual

internal interface NullableBooleanThreadLocalKmp {
    fun get(): Boolean?

    fun remove()

    fun set(value: Boolean?)
}

internal fun nullableBooleanThreadLocalKmp(): NullableBooleanThreadLocalKmp = nullableBooleanThreadLocalImpl()

internal fun nullableBooleanThreadLocalImpl(): NullableBooleanThreadLocalKmp = linkToActual()
