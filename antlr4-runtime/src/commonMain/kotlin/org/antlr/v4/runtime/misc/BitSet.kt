/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc

class BitSet {
    private var words: LongArray = LongArray(1)

    constructor()

    constructor(bits: IntArray) {
        for (bit in bits) set(bit)
    }

    fun set(bitIndex: Int) {
        require(bitIndex >= 0)
        ensureCapacity(bitIndex)
        words[wordIndex(bitIndex)] = words[wordIndex(bitIndex)] or (1L shl bitIndex)
    }

    fun set(bitIndex: Int, value: Boolean) {
        if (value) set(bitIndex) else clear(bitIndex)
    }

    fun clear(bitIndex: Int) {
        if (bitIndex < 0) return
        val idx = wordIndex(bitIndex)
        if (idx < words.size) {
            words[idx] = words[idx] and (1L shl bitIndex).inv()
        }
    }

    fun clear() {
        words.fill(0)
    }

    fun get(bitIndex: Int): Boolean {
        require(bitIndex >= 0)
        val idx = wordIndex(bitIndex)
        if (idx >= words.size) return false
        return words[idx] and (1L shl bitIndex) != 0L
    }

    fun or(other: BitSet) {
        ensureCapacity(other.words.size * BITS_PER_WORD - 1)
        for (i in other.words.indices) {
            words[i] = words[i] or other.words[i]
        }
    }

    fun and(other: BitSet) {
        val limit = minOf(words.size, other.words.size)
        for (i in limit until words.size) words[i] = 0
        for (i in 0 until limit) words[i] = words[i] and other.words[i]
    }

    fun cardinality(): Int {
        var count = 0
        for (word in words) count += word.countOneBits().toInt()
        return count
    }

    fun nextSetBit(fromIndex: Int): Int {
        if (fromIndex < 0) return nextSetBit(0)
        var idx = wordIndex(fromIndex)
        if (idx >= words.size) return -1
        var word = words[idx] and (MASK shl fromIndex)
        while (true) {
            if (word != 0L) return (idx * BITS_PER_WORD) + word.countTrailingZeroBits()
            idx++
            if (idx >= words.size) return -1
            word = words[idx]
        }
    }

    fun isEmpty(): Boolean {
        for (word in words) if (word != 0L) return false
        return true
    }

    fun toIntArray(): IntArray {
        val result = mutableListOf<Int>()
        var i = nextSetBit(0)
        while (i >= 0) { result.add(i); i = nextSetBit(i + 1) }
        return result.toIntArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        other as BitSet
        trimTrailingZeros(); other.trimTrailingZeros()
        return words.contentEquals(other.words)
    }

    override fun hashCode(): Int {
        trimTrailingZeros()
        return words.contentHashCode()
    }

    override fun toString(): String = "{" + toIntArray().joinToString(", ") + "}"

    private fun ensureCapacity(bitIndex: Int) {
        val needed = wordIndex(bitIndex) + 1
        if (needed <= words.size) return
        val newWords = LongArray(maxOf(needed, words.size * 2))
        words.copyInto(newWords, 0, 0, words.size)
        words = newWords
    }

    private fun trimTrailingZeros() {
        var newLen = words.size
        while (newLen > 0 && words[newLen - 1] == 0L) newLen--
        if (newLen != words.size) words = words.copyOf(newLen)
    }

    companion object {
        private const val BITS_PER_WORD = 64
        private const val MASK: Long = -1L
        private fun wordIndex(bitIndex: Int): Int = bitIndex shr 6
    }
}
