// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.fastutil.ints


interface MutableInt2IntMap : Int2IntMap {
  fun put(key: Int, value: Int): Int
  fun remove(key: Int): Int

  operator fun set(key: Int, value: Int): Int = put(key, value)
}