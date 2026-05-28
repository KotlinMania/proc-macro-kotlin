/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package org.antlr.v4.runtime.misc

/**
 *
 * @author Sam Harwell
 */
open class IntList {
    private var _data: IntArray

    private var _size = 0

    constructor() {
        _data = org.antlr.v4.runtime.misc.IntList.Companion.EMPTY_DATA
    }

    constructor(capacity: Int) {
        require(capacity >= 0)

        if (capacity == 0) {
            _data = org.antlr.v4.runtime.misc.IntList.Companion.EMPTY_DATA
        } else {
            _data = IntArray(capacity)
        }
    }

    constructor(list: IntList) {
        _data = list._data.copyOf()
        _size = list._size
    }

    constructor(list: Collection<Int?>) : this(list.size) {
        for (value in list) {
            add(value)
        }
    }

    fun add(value: Int) {
        if (_data.size == _size) {
            ensureCapacity(_size + 1)
        }

        _data[_size] = value
        _size++
    }

    fun addAll(array: IntArray) {
        ensureCapacity(_size + array.size)
        array.copyInto(_data, _size, 0, array.size)
        _size += array.size
    }

    fun addAll(list: IntList) {
        ensureCapacity(_size + list._size)
        list._data.copyInto(_data, _size, 0, list._size)
        _size += list._size
    }

    fun addAll(list: Collection<Int?>) {
        ensureCapacity(_size + list.size)
        var current = 0
        for (x in list) {
            _data[_size + current] = x
            current++
        }
        _size += list.size
    }

    fun get(index: Int): Int {
        if (index < 0 || index >= _size) {
            throw IndexOutOfBoundsException()
        }

        return _data[index]
    }

    fun contains(value: Int): Boolean {
        for (i in 0..<_size) {
            if (_data[i] == value) {
                return true
            }
        }

        return false
    }

    fun set(
        index: Int,
        value: Int,
    ): Int {
        if (index < 0 || index >= _size) {
            throw IndexOutOfBoundsException()
        }

        val previous = _data[index]
        _data[index] = value
        return previous
    }

    fun removeAt(index: Int): Int {
        val value = get(index)
        _data.copyInto(_data, index, index + 1, _size)
        _data[_size - 1] = 0
        _size--
        return value
    }

    fun removeRange(
        fromIndex: Int,
        toIndex: Int,
    ) {
        if (fromIndex < 0 || toIndex < 0 || fromIndex > _size || toIndex > _size) {
            throw IndexOutOfBoundsException()
        }
        require(fromIndex <= toIndex)

        _data.copyInto(_data, fromIndex, toIndex, _size)
        _data.fill(0, _size - (toIndex - fromIndex), _size)
        _size -= (toIndex - fromIndex)
    }

    val isEmpty: Boolean
        get() = _size == 0

    fun size(): Int = _size

    fun trimToSize() {
        if (_data.size == _size) {
            return
        }

        _data = _data.copyOf(_size)
    }

    fun clear() {
        _data.fill(0, 0, _size)
        _size = 0
    }

    fun toArray(): IntArray {
        if (_size == 0) {
            return org.antlr.v4.runtime.misc.IntList.Companion.EMPTY_DATA
        }

        return _data.copyOf(_size)
    }

    fun sort() {
        _data.sort(0, _size)
    }

    /**
     * Compares the specified object with this list for equality.  Returns
     * `true` if and only if the specified object is also an [IntList],
     * both lists have the same size, and all corresponding pairs of elements in
     * the two lists are equal.  In other words, two lists are defined to be
     * equal if they contain the same elements in the same order.
     *
     *
     * This implementation first checks if the specified object is this
     * list. If so, it returns `true`; if not, it checks if the
     * specified object is an [IntList]. If not, it returns `false`;
     * if so, it checks the size of both lists. If the lists are not the same size,
     * it returns `false`; otherwise it iterates over both lists, comparing
     * corresponding pairs of elements.  If any comparison returns `false`,
     * this method returns `false`.
     *
     * @param o the object to be compared for equality with this list
     * @return `true` if the specified object is equal to this list
     */
    override fun equals(o: Any?): Boolean {
        if (o === this) {
            return true
        }

        if (o !is IntList) {
            return false
        }

        val other = o
        if (_size != other._size) {
            return false
        }

        for (i in 0..<_size) {
            if (_data[i] != other._data[i]) {
                return false
            }
        }

        return true
    }

    /**
     * Returns the hash code value for this list.
     *
     *
     * This implementation uses exactly the code that is used to define the
     * list hash function in the documentation for the [List.hashCode]
     * method.
     *
     * @return the hash code value for this list
     */
    override fun hashCode(): Int {
        var hashCode = 1
        for (i in 0..<_size) {
            hashCode = 31 * hashCode + _data[i]
        }

        return hashCode
    }

    /**
     * Returns a string representation of this list.
     */
    override fun toString(): String = toArray().contentToString()

    fun binarySearch(key: Int): Int = _data.binarySearch(key, 0, _size)

    fun binarySearch(
        fromIndex: Int,
        toIndex: Int,
        key: Int,
    ): Int {
        if (fromIndex < 0 || toIndex < 0 || fromIndex > _size || toIndex > _size) {
            throw IndexOutOfBoundsException()
        }
        require(fromIndex <= toIndex)

        return _data.binarySearch(key, fromIndex, toIndex)
    }

    private fun ensureCapacity(capacity: Int) {
        if (capacity < 0 || capacity > org.antlr.v4.runtime.misc.IntList.Companion.MAX_ARRAY_SIZE) {
            throw OutOfMemoryError()
        }

        var newLength: Int
        if (_data.size == 0) {
            newLength = org.antlr.v4.runtime.misc.IntList.Companion.INITIAL_SIZE
        } else {
            newLength = _data.size
        }

        while (newLength < capacity) {
            newLength = newLength * 2
            if (newLength < 0 || newLength > org.antlr.v4.runtime.misc.IntList.Companion.MAX_ARRAY_SIZE) {
                newLength = org.antlr.v4.runtime.misc.IntList.Companion.MAX_ARRAY_SIZE
            }
        }

        _data = _data.copyOf(newLength)
    }

    /** Convert the int list to a char array where values > 0x7FFFF take 2 bytes. TODO?????
     * If all values are less
     * than the 0x7FFF 16-bit code point limit (1 bit taken to indicatethen this is just a char array
     * of 16-bit char as usual. For values in the supplementary range, encode
     * them as two UTF-16 code units.
     */
    fun toCharArray(): CharArray? {
        // Optimize for the common case (all data values are
        // < 0xFFFF) to avoid an extra scan
        var resultArray: CharArray? = CharArray(_size)
        var resultIdx = 0
        var calculatedPreciseResultSize = false
        for (i in 0..<_size) {
            val codePoint = _data[i]
            // Calculate the precise result size if we encounter
            // a code point > 0xFFFF
            if (!calculatedPreciseResultSize &&
                codePoint >= 0x10000
            ) {
                resultArray = resultArray!!.copyOf(charArraySize())
                calculatedPreciseResultSize = true
            }
            val chars = Char.toChars(codePoint)
            chars.copyInto(resultArray!!, resultIdx)
            resultIdx += chars.size
        }
        return resultArray
    }

    private fun charArraySize(): Int {
        var result = 0
        for (i in 0..<_size) {
            result += Char.charCount(_data[i])
        }
        return result
    }

    companion object {
        private val EMPTY_DATA = IntArray(0)

        private const val INITIAL_SIZE = 4
        private val MAX_ARRAY_SIZE: Int = Int.MAX_VALUE - 8
    }
}
