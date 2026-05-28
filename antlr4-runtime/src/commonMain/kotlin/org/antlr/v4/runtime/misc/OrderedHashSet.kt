/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc

class OrderedHashSet<T> : LinkedHashSet<T>() {
    protected var elements: ArrayList<T> = ArrayList()

    fun get(i: Int): T = elements[i]

    fun set(i: Int, value: T): T {
        val oldElement = elements[i]
        elements[i] = value
        super.remove(oldElement)
        super.add(value)
        return oldElement
    }

    fun remove(i: Int): Boolean {
        val o = elements.removeAt(i)
        return super.remove(o)
    }

    override fun add(element: T): Boolean {
        val result = super.add(element)
        if (result) elements.add(element)
        return result
    }

    override fun remove(element: T): Boolean = throw UnsupportedOperationException()

    override fun clear() {
        elements.clear()
        super.clear()
    }

    override fun hashCode(): Int = elements.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is OrderedHashSet<*>) return false
        return elements == other.elements
    }

    override fun iterator(): MutableIterator<T> = elements.iterator()

    fun elements(): List<T> = elements

    override fun toArray(): Array<Any> = elements.toTypedArray()

    override fun toString(): String = elements.toString()
}
