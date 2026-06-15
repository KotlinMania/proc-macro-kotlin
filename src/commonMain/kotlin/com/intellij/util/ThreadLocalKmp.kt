// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import io.github.kotlinmania.threadlocal.ThreadLocal

internal interface NullableBooleanThreadLocalKmp {
    fun get(): Boolean?

    fun remove()

    fun set(value: Boolean?)
}

internal fun nullableBooleanThreadLocalKmp(): NullableBooleanThreadLocalKmp = nullableBooleanThreadLocalImpl()

internal fun nullableBooleanThreadLocalImpl(): NullableBooleanThreadLocalKmp = NullableBooleanThreadLocal()

private class NullableBooleanSlot(
    var value: Boolean?,
)

private class NullableBooleanThreadLocal : NullableBooleanThreadLocalKmp {
    private val slot: ThreadLocal<NullableBooleanSlot> = ThreadLocal()

    override fun get(): Boolean? = slot.get()?.value

    override fun remove() {
        slot.get()?.value = null
    }

    override fun set(value: Boolean?) {
        slot.getOr { NullableBooleanSlot(null) }.value = value
    }
}
