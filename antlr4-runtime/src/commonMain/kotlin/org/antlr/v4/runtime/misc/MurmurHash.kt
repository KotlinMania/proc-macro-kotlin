/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc

object MurmurHash {
    private const val DEFAULT_SEED = 0

    @kotlin.jvm.JvmOverloads
    fun initialize(seed: Int = DEFAULT_SEED): Int = seed

    fun update(hash: Int, value: Int): Int {
        val c1 = -0x3361d2af
        val c2 = 0x1B873593
        val r1 = 15
        val r2 = 13
        val m = 5
        val n = -0x19ab949c

        var k = value
        k = k * c1
        k = (k shl r1) or (k ushr (32 - r1))
        k = k * c2

        var h = hash xor k
        h = (h shl r2) or (h ushr (32 - r2))
        h = h * m + n
        return h
    }

    fun update(hash: Int, value: Any?): Int = update(hash, value?.hashCode() ?: 0)

    fun finish(hash: Int, numberOfWords: Int): Int {
        var h = hash xor (numberOfWords * 4)
        h = h xor (h ushr 16)
        h = h * -0x7a143595
        h = h xor (h ushr 13)
        h = h * -0x3d4d51cb
        h = h xor (h ushr 16)
        return h
    }

    fun <T> hashCode(data: Array<T>, seed: Int): Int {
        var hash = initialize(seed)
        for (value in data) {
            hash = update(hash, value)
        }
        hash = finish(hash, data.size)
        return hash
    }
}
