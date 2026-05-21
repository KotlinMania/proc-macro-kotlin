// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.fastutil.ints


interface Int2IntMap {
  val size: Int
  val keys: IntIterator
  val values: IntIterator
  val entries: Iterator<Int2IntEntry>

  operator fun get(key: Int): Int
}
