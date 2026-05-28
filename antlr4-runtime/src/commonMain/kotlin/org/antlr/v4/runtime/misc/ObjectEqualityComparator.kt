/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc

/**
 * This default implementation of [EqualityComparator] uses object equality
 * for comparisons by calling [Any.hashCode] and [Any.equals].
 *
 * @author Sam Harwell
 */
class AnyEqualityComparator : AbstractEqualityComparator<Any?>() {
    /**
     * {@inheritDoc}
     *
     *
     * This implementation returns
     * `obj.`[hashCode()][Any.hashCode].
     */
    fun hashCode(obj: Any?): Int {
        if (obj == null) {
            return 0
        }

        return obj.hashCode()
    }

    /**
     * {@inheritDoc}
     *
     *
     * This implementation relies on object equality. If both objects are
     * `null`, this method returns `true`. Otherwise if only
     * `a` is `null`, this method returns `false`. Otherwise,
     * this method returns the result of
     * `a.`[equals][Any.equals]`(b)`.
     */
    fun equals(a: Any?, b: Any?): Boolean {
        if (a == null) {
            return b == null
        }

        return a.equals(b)
    }

    companion object {
        val INSTANCE: AnyEqualityComparator = org.antlr.v4.runtime.misc.AnyEqualityComparator()
    }
}
