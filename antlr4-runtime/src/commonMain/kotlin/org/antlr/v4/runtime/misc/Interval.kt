/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc

class Interval(
    val a: Int,
    val b: Int,
) {
    fun length(): Int = if (b < a) 0 else b - a + 1

    override fun equals(other: Any?): Boolean {
        if (other !is Interval) return false
        return a == other.a && b == other.b
    }

    override fun hashCode(): Int {
        var hash = 23
        hash = hash * 31 + a
        hash = hash * 31 + b
        return hash
    }

    fun startsBeforeDisjoint(other: Interval): Boolean = a < other.a && b < other.a

    fun startsBeforeNonDisjoint(other: Interval): Boolean = a <= other.a && b >= other.a

    fun startsAfter(other: Interval): Boolean = a > other.a

    fun startsAfterDisjoint(other: Interval): Boolean = a > other.b

    fun startsAfterNonDisjoint(other: Interval): Boolean = a > other.a && a <= other.b

    fun disjoint(other: Interval): Boolean = startsBeforeDisjoint(other) || startsAfterDisjoint(other)

    fun adjacent(other: Interval): Boolean = a == other.b + 1 || b == other.a - 1

    fun properlyContains(other: Interval): Boolean = other.a >= a && other.b <= b

    fun union(other: Interval): Interval = of(minOf(a, other.a), maxOf(b, other.b))

    fun intersection(other: Interval): Interval = of(maxOf(a, other.a), minOf(b, other.b))

    fun differenceNotProperlyContained(other: Interval): Interval? =
        if (other.startsBeforeNonDisjoint(this)) {
            of(maxOf(a, other.b + 1), b)
        } else if (other.startsAfterNonDisjoint(this)) {
            of(a, other.a - 1)
        } else {
            null
        }

    override fun toString(): String = "$a..$b"

    companion object {
        val INVALID = Interval(-1, -2)

        fun of(
            a: Int,
            b: Int,
        ): Interval = Interval(a, b)
    }
}
