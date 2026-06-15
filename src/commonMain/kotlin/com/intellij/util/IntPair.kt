// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

class IntPair(
    val first: Int,
    val second: Int,
) {
    override fun hashCode(): Int = 31 * first + second

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IntPair) return false

        return first == other.first && second == other.second
    }

    override fun toString(): String = "first=$first, second=$second"
}
