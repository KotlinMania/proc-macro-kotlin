// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import com.intellij.platform.syntax.impl.util.MutableBitSet
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class BitSetTest {
    @Test
    fun empty() {
        val set = MutableBitSet()
        assertFalse(set.contains(0))
        assertFalse(set.contains(1))
        assertFalse(set.contains(2))
    }

    @Test
    fun addAndRemove() {
        for (i in 0..1000) {
            val set = MutableBitSet()
            set.add(i)
            assertTrue(set.contains(i), "Failed at $i")
            assertFalse(set.contains(i + 1), "Failed at $i")
            if (i > 0) {
                assertFalse(set.contains(i - 1), "Failed at $i")
            }

            set.remove(i)
            assertFalse(set.contains(i), "Failed at $i")
        }
    }

    @Test
    fun addMany() {
        val set = MutableBitSet()
        for (i in 0..1000) {
            set.add(i)
        }

        for (i in 0..1000) {
            assertTrue(set.contains(i), "Not contains $i")
        }

        assertFalse(set.contains(1001))

        set.remove(500)
        assertFalse(set.contains(500))
    }
}
