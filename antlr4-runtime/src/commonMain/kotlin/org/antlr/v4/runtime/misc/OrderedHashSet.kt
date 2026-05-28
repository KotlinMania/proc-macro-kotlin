/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc

class OrderedHashSet<T> : MutableSet<T> {
    private val backingSet: MutableSet<T> = LinkedHashSet()
    private val elements: MutableList<T> = ArrayList()

    fun get(i: Int): T = elements[i]

    fun set(
        i: Int,
        value: T,
    ): T {
        val oldElement = elements[i]
        elements[i] = value
        backingSet.remove(oldElement)
        backingSet.add(value)
        return oldElement
    }

    fun remove(i: Int): Boolean {
        val o = elements.removeAt(i)
        return backingSet.remove(o)
    }

    override fun add(element: T): Boolean {
        val result = backingSet.add(element)
        if (result) elements.add(element)
        return result
    }

    override fun remove(element: T): Boolean = throw UnsupportedOperationException()

    override fun clear() {
        elements.clear()
        backingSet.clear()
    }

    override fun hashCode(): Int = elements.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is OrderedHashSet<*>) return false
        return elements == other.elements
    }

    override fun iterator(): MutableIterator<T> = elements.iterator()

    fun elements(): List<T> = elements

    @Suppress("UNCHECKED_CAST")
    fun toArray(): Array<Any> {
        val result = arrayOfNulls<Any>(elements.size)
        for (i in elements.indices) {
            result[i] = elements[i]
        }
        return result as Array<Any>
    }

    override fun toString(): String = elements.toString()

    override val size: Int get() = backingSet.size

    override fun isEmpty(): Boolean = backingSet.isEmpty()

    override fun contains(element: T): Boolean = backingSet.contains(element)

    override fun containsAll(elements: Collection<T>): Boolean = backingSet.containsAll(elements)

    override fun addAll(elements: Collection<T>): Boolean {
        var changed = false
        for (element in elements) {
            if (add(element)) changed = true
        }
        return changed
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        val toRemove = this.elements.filter { it !in elements }
        return removeAll(toRemove)
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        var changed = false
        for (element in elements) {
            if (backingSet.contains(element)) {
                this.elements.remove(element)
                backingSet.remove(element)
                changed = true
            }
        }
        return changed
    }
}
