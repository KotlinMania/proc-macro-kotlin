/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc

import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.Vocabulary
import org.antlr.v4.runtime.VocabularyImpl

/**
 * This class implements the [IntSet] backed by a sorted array of
 * non-overlapping intervals. It is particularly efficient for representing
 * large collections of numbers, where the majority of elements appear as part
 * of a sequential range of numbers that are all part of the set. For example,
 * the set { 1, 2, 3, 4, 7, 8 } may be represented as { [1, 4], [7, 8] }.
 *
 *
 *
 * This class is able to represent sets containing any combination of values in
 * the range [Int.MIN_VALUE] to [Int.MAX_VALUE]
 * (inclusive).
 */
class IntervalSet : IntSet {
    /** The list of sorted, disjoint intervals.  */
    internal var intervals: MutableList<Interval> = ArrayList()

    protected var readonly: Boolean = false

    constructor(intervals: MutableList<Interval>?) {
        this.intervals = intervals
    }

    constructor(set: IntervalSet?) : this() {
        addAll(set)
    }

    constructor(vararg els: Int) {
        if (els == null) {
            intervals = ArrayList<Interval>(2) // most sets are 1 or 2 elements
        } else {
            intervals = ArrayList<Interval>(els.size)
            for (e in els) add(e)
        }
    }

    override fun clear() {
        check(!readonly) { "can't alter readonly IntervalSet" }
        intervals.clear()
    }

    /** Add a single element to the set.  An isolated element is stored
     * as a range el..el.
     */
    fun add(el: Int) {
        check(!readonly) { "can't alter readonly IntervalSet" }
        add(el, el)
    }

    /** Add interval; i.e., add all integers from a to b to set.
     * If b&lt;a, do nothing.
     * Keep list in sorted order (by left range value).
     * If overlap, combine ranges.  For example,
     * If this is {1..5, 10..20}, adding 6..7 yields
     * {1..5, 6..7, 10..20}.  Adding 4..8 yields {1..8, 10..20}.
     */
    fun add(a: Int, b: Int) {
        add(Interval.of(a, b))
    }

    // copy on write so we can cache a..a intervals and sets of that
    protected fun add(addition: Interval) {
        check(!readonly) { "can't alter readonly IntervalSet" }
        //println("add "+addition+" to "+intervals.toString());
        if (addition.b < addition.a) {
            return
        }
        // find position in list
        // Use iterators as we modify list in place
        val iter: ListIterator<Interval> = intervals.listIterator()
        while (iter.hasNext()) {
            val r: Interval? = iter.next()
            if (addition.equals(r)) {
                return
            }
            if (addition.adjacent(r) || !addition.disjoint(r)) {
                // next to each other, make a single larger interval
                val bigger: Interval = addition.union(r)
                iter.set(bigger)
                // make sure we didn't just create an interval that
                // should be merged with next interval in list
                while (iter.hasNext()) {
                    val next: Interval? = iter.next()
                    if (!bigger.adjacent(next) && bigger.disjoint(next)) {
                        break
                    }

                    // if we bump up against or overlap next, merge
                    iter.remove() // remove this one
                    iter.previous() // move backwards to what we just set
                    iter.set(bigger.union(next)) // set to 3 merged ones
                    iter.next() // first call to next after previous duplicates the result
                }
                return
            }
            if (addition.startsBeforeDisjoint(r)) {
                // insert before r
                iter.previous()
                iter.add(addition)
                return
            }
        }
        // ok, must be after last interval (and disjoint from last interval)
        // just add it
        intervals.add(addition)
    }
    override fun addAll(set: IntSet?): IntervalSet {
        if (set == null) {
            return this
        }

        if (set is IntervalSet) {
            val other = set
            // walk set and add each interval
            val n: Int = other.intervals.size
            for (i in 0..<n) {
                val I: Interval = other.intervals.get(i)
                this.add(I.a, I.b)
            }
        } else {
            for (value in set.toList()) {
                add(value)
            }
        }

        return this
    }

    fun complement(minElement: Int, maxElement: Int): IntervalSet? {
        return this.complement(org.antlr.v4.runtime.misc.IntervalSet.Companion.of(minElement, maxElement))
    }

    /** {@inheritDoc}  */
    fun complement(vocabulary: IntSet?): IntervalSet? {
        if (vocabulary == null || vocabulary.isNil()) {
            return null // nothing in common with null set
        }

        val vocabularyIS: IntervalSet?
        if (vocabulary is IntervalSet) {
            vocabularyIS = vocabulary
        } else {
            vocabularyIS = org.antlr.v4.runtime.misc.IntervalSet()
            vocabularyIS!!.addAll(vocabulary)
        }

        return vocabularyIS.subtract(this)
    }
    fun subtract(a: IntSet?): IntervalSet {
        if (a == null || a.isNil()) {
            return org.antlr.v4.runtime.misc.IntervalSet(this)
        }

        if (a is IntervalSet) {
            return org.antlr.v4.runtime.misc.IntervalSet.Companion.subtract(this, a)
        }

        val other: IntervalSet = org.antlr.v4.runtime.misc.IntervalSet()
        other.addAll(a)
        return org.antlr.v4.runtime.misc.IntervalSet.Companion.subtract(this, other)
    }
    fun or(a: IntSet?): IntervalSet {
        val o: IntervalSet = org.antlr.v4.runtime.misc.IntervalSet()
        o.addAll(this)
        o.addAll(a)
        return o
    }

    /** {@inheritDoc}  */
    fun and(other: IntSet?): IntervalSet? {
        if (other == null) { //|| !(other instanceof IntervalSet) ) {
            return null // nothing in common with null set
        }

        val myIntervals: MutableList<Interval> = this.intervals
        val theirIntervals: MutableList<Interval> = (other as IntervalSet).intervals
        var intersection: IntervalSet? = null
        val mySize: Int = myIntervals.size
        val theirSize: Int = theirIntervals.size
        var i = 0
        var j = 0
        // iterate down both interval lists looking for nondisjoint intervals
        while (i < mySize && j < theirSize) {
            val mine: Interval = myIntervals!!.get(i)
            val theirs: Interval = theirIntervals!!.get(j)
            //println("mine="+mine+" and theirs="+theirs);
            if (mine.startsBeforeDisjoint(theirs)) {
                // move this iterator looking for interval that might overlap
                i++
            } else if (theirs.startsBeforeDisjoint(mine)) {
                // move other iterator looking for interval that might overlap
                j++
            } else if (mine.properlyContains(theirs)) {
                // overlap, add intersection, get next theirs
                if (intersection == null) {
                    intersection = org.antlr.v4.runtime.misc.IntervalSet()
                }
                intersection.add(mine.intersection(theirs))
                j++
            } else if (theirs.properlyContains(mine)) {
                // overlap, add intersection, get next mine
                if (intersection == null) {
                    intersection = org.antlr.v4.runtime.misc.IntervalSet()
                }
                intersection.add(mine.intersection(theirs))
                i++
            } else if (!mine.disjoint(theirs)) {
                // overlap, add intersection
                if (intersection == null) {
                    intersection = org.antlr.v4.runtime.misc.IntervalSet()
                }
                intersection.add(mine.intersection(theirs))
                // Move the iterator of lower range [a..b], but not
                // the upper range as it may contain elements that will collide
                // with the next iterator. So, if mine=[0..115] and
                // theirs=[115..200], then intersection is 115 and move mine
                // but not theirs as theirs may collide with the next range
                // in thisIter.
                // move both iterators to next ranges
                if (mine.startsAfterNonDisjoint(theirs)) {
                    j++
                } else if (theirs.startsAfterNonDisjoint(mine)) {
                    i++
                }
            }
        }
        if (intersection == null) {
            return org.antlr.v4.runtime.misc.IntervalSet()
        }
        return intersection
    }

    /** {@inheritDoc}  */
    override fun contains(el: Int): Boolean {
        val n: Int = intervals.size
        var l = 0
        var r = n - 1
        // Binary search for the element in the (sorted,
        // disjoint) array of intervals.
        while (l <= r) {
            val m = (l + r) / 2
            val I: Interval = intervals.get(m)
            val a: Int = I.a
            val b: Int = I.b
            if (b < el) {
                l = m + 1
            } else if (a > el) {
                r = m - 1
            } else { // el >= a && el <= b
                return true
            }
        }
        return false
    }
    val isNil: Boolean
        /** {@inheritDoc}  */
        get() = intervals.isEmpty

    val maxElement: Int
        /**
         * Returns the maximum value contained in the set if not isNil().
         *
         * @return the maximum value contained in the set.
         * @throws RuntimeException if set is empty
         */
        get() {
            if (this.isNil) {
                throw RuntimeException("set is empty")
            }
            val last: Interval = intervals.get(intervals.size - 1)
            return last.b
        }

    val minElement: Int
        /**
         * Returns the minimum value contained in the set if not isNil().
         *
         * @return the minimum value contained in the set.
         * @throws RuntimeException if set is empty
         */
        get() {
            if (this.isNil) {
                throw RuntimeException("set is empty")
            }

            return intervals.get(0).a
        }

    /** Return a list of Interval objects.  */
    fun getIntervals(): MutableList<Interval> {
        return intervals
    }
    override fun hashCode(): Int {
        var hash: Int = MurmurHash.initialize()
        for (I in intervals!!) {
            hash = MurmurHash.update(hash, I.a)
            hash = MurmurHash.update(hash, I.b)
        }

        hash = MurmurHash.finish(hash, intervals.size * 2)
        return hash
    }

    /** Are two IntervalSets equal?  Because all intervals are sorted
     * and disjoint, equals is a simple linear walk over both lists
     * to make sure they are the same.  Interval.equals() is used
     * by the List.equals() method to check the ranges.
     */
    override fun equals(obj: Any?): Boolean {
        if (obj == null || obj !is IntervalSet) {
            return false
        }
        val other = obj
        return this.intervals.equals(other.intervals)
    }
    override fun toString(): String? {
        return toString(false)
    }

    override fun toString(elemAreChar: Boolean): String? {
        val buf: StringBuilder = StringBuilder()
        if (this.intervals.isEmpty) {
            return "{}"
        }
        if (this.size > 1) {
            buf.append("{")
        }
        val iter: Iterator<Interval> = this.intervals.iterator()
        while (iter.hasNext()) {
            val I: Interval = iter.next()
            val a: Int = I.a
            val b: Int = I.b
            if (a == b) {
                if (a == Token.EOF) buf.append("<EOF>")
                else if (elemAreChar) buf.append("'").appendCodePoint(a).append("'")
                else buf.append(a)
            } else {
                if (elemAreChar) buf.append("'").appendCodePoint(a).append("'..'").appendCodePoint(b).append("'")
                else buf.append(a).append("..").append(b)
            }
            if (iter.hasNext()) {
                buf.append(", ")
            }
        }
        if (this.size > 1) {
            buf.append("}")
        }
        return buf.toString()
    }

    @Deprecated
    @Deprecated("Use {@link #toString(Vocabulary)} instead.")
    override fun toString(tokenNames: Array<String?>?): String? {
        return toString(VocabularyImpl.fromTokenNames(tokenNames))
    }

    override fun toString(vocabulary: Vocabulary): String? {
        val buf: StringBuilder = StringBuilder()
        if (this.intervals.isEmpty) {
            return "{}"
        }
        if (this.size > 1) {
            buf.append("{")
        }
        val iter: Iterator<Interval> = this.intervals.iterator()
        while (iter.hasNext()) {
            val I: Interval = iter.next()
            val a: Int = I.a
            val b: Int = I.b
            if (a == b) {
                buf.append(elementName(vocabulary, a))
            } else {
                for (i in a..b) {
                    if (i > a) buf.append(", ")
                    buf.append(elementName(vocabulary, i))
                }
            }
            if (iter.hasNext()) {
                buf.append(", ")
            }
        }
        if (this.size > 1) {
            buf.append("}")
        }
        return buf.toString()
    }

    @Deprecated
    @Deprecated("Use {@link #elementName(Vocabulary, int)} instead.")
    protected fun elementName(tokenNames: Array<String?>?, a: Int): String? {
        return elementName(VocabularyImpl.fromTokenNames(tokenNames), a)
    }


    protected fun elementName(vocabulary: Vocabulary, a: Int): String? {
        if (a == Token.EOF) {
            return "<EOF>"
        } else if (a == Token.EPSILON) {
            return "<EPSILON>"
        } else {
            return vocabulary.getDisplayName(a)
        }
    }
    override fun size(): Int {
        var n = 0
        val numIntervals: Int = intervals.size
        if (numIntervals == 1) {
            val firstInterval: Interval = this.intervals.get(0)
            return firstInterval.b - firstInterval.a + 1
        }
        for (i in 0..<numIntervals) {
            val I: Interval = intervals.get(i)
            n += (I.b - I.a + 1)
        }
        return n
    }

    fun toIntList(): IntList {
        val values: IntList = IntList(size())
        val n: Int = intervals.size
        for (i in 0..<n) {
            val I: Interval = intervals.get(i)
            val a: Int = I.a
            val b: Int = I.b
            for (v in a..b) {
                values.add(v)
            }
        }
        return values
    }
    fun toList(): List<Int> {
        val values: MutableList<Int> = ArrayList()
        val n: Int = intervals.size
        for (i in 0..<n) {
            val I: Interval = intervals.get(i)
            val a: Int = I.a
            val b: Int = I.b
            for (v in a..b) {
                values.add(v)
            }
        }
        return values
    }

    fun toSet(): Set<Int?> {
        val s: Set<Int?> = HashSet<Int?>()
        for (I in intervals!!) {
            val a: Int = I.a
            val b: Int = I.b
            for (v in a..b) {
                s.add(v)
            }
        }
        return s
    }

    /** Get the ith element of ordered set.  Used only by RandomPhrase so
     * don't bother to implement if you're not doing that for a new
     * ANTLR code gen target.
     */
    fun get(i: Int): Int {
        val n: Int = intervals.size
        var index = 0
        for (j in 0..<n) {
            val I: Interval = intervals.get(j)
            val a: Int = I.a
            val b: Int = I.b
            for (v in a..b) {
                if (index == i) {
                    return v
                }
                index++
            }
        }
        return -1
    }

    override fun toArray(): IntArray {
        return toIntList().toArray()
    }
    override fun remove(el: Int) {
        check(!readonly) { "can't alter readonly IntervalSet" }
        val n: Int = intervals.size
        for (i in 0..<n) {
            var I: Interval = intervals.get(i)
            val a: Int = I.a
            val b: Int = I.b
            if (el < a) {
                break // list is sorted and el is before this interval; not here
            }
            // if whole interval x..x, rm
            if (el == a && el == b) {
                intervals.remove(i)
                break
            }
            // if on left edge x..b, adjust left
            if (el == a) {
                I = Interval.of(a + 1, b)
                intervals.set(i, I)
                break
            }
            // if on right edge a..x, adjust right
            if (el == b) {
                I = Interval.of(a, b - 1)
                intervals.set(i, I)
                break
            }
            // if in middle a..x..b, split interval
            if (el > a && el < b) { // found in this interval
                val oldb: Int = I.b
                I = Interval.of(a, el - 1) // [a..x-1]
                intervals.set(i, I)
                add(el + 1, oldb) // add [x+1..b]
            }
        }
    }

    fun isReadonly(): Boolean {
        return readonly
    }

    fun setReadonly(readonly: Boolean) {
        check(!(this.readonly && !readonly)) { "can't alter readonly IntervalSet" }
        this.readonly = readonly
    }

    companion object {
        val COMPLETE_CHAR_SET: IntervalSet =
            org.antlr.v4.runtime.misc.IntervalSet.Companion.of(Lexer.MIN_CHAR_VALUE, Lexer.MAX_CHAR_VALUE)

        init {
            org.antlr.v4.runtime.misc.IntervalSet.Companion.COMPLETE_CHAR_SET.setReadonly(true)
        }

        val EMPTY_SET: IntervalSet = org.antlr.v4.runtime.misc.IntervalSet()

        init {
            org.antlr.v4.runtime.misc.IntervalSet.Companion.EMPTY_SET.setReadonly(true)
        }

        /** Create a set with a single element, el.  */
        fun of(a: Int): IntervalSet {
            val s: IntervalSet = org.antlr.v4.runtime.misc.IntervalSet()
            s.add(a)
            return s
        }

        /** Create a set with all ints within range [a..b] (inclusive)  */
        fun of(a: Int, b: Int): IntervalSet {
            val s: IntervalSet = org.antlr.v4.runtime.misc.IntervalSet()
            s.add(a, b)
            return s
        }

        /** combine all sets in the array returned the or'd value  */
        fun or(sets: Array<IntervalSet?>): IntervalSet {
            val r: IntervalSet = org.antlr.v4.runtime.misc.IntervalSet()
            for (s in sets) r.addAll(s)
            return r
        }

        /**
         * Compute the set difference between two interval sets. The specific
         * operation is `left - right`. If either of the input sets is
         * `null`, it is treated as though it was an empty set.
         */
        fun subtract(left: IntervalSet?, right: IntervalSet?): IntervalSet {
            if (left == null || left.isNil) {
                return org.antlr.v4.runtime.misc.IntervalSet()
            }

            val result: IntervalSet = org.antlr.v4.runtime.misc.IntervalSet(left)
            if (right == null || right.isNil) {
                // right set has no elements; just return the copy of the current set
                return result
            }

            var resultI = 0
            var rightI = 0
            while (resultI < result.intervals.size && rightI < right.intervals.size) {
                val resultInterval: Interval = result.intervals.get(resultI)
                val rightInterval: Interval = right.intervals.get(rightI)

                // operation: (resultInterval - rightInterval) and update indexes
                if (rightInterval.b < resultInterval.a) {
                    rightI++
                    continue
                }

                if (rightInterval.a > resultInterval.b) {
                    resultI++
                    continue
                }

                var beforeCurrent: Interval? = null
                var afterCurrent: Interval? = null
                if (rightInterval.a > resultInterval.a) {
                    beforeCurrent = Interval(resultInterval.a, rightInterval.a - 1)
                }

                if (rightInterval.b < resultInterval.b) {
                    afterCurrent = Interval(rightInterval.b + 1, resultInterval.b)
                }

                if (beforeCurrent != null) {
                    if (afterCurrent != null) {
                        // split the current interval into two
                        result.intervals.set(resultI, beforeCurrent)
                        result.intervals.add(resultI + 1, afterCurrent)
                        resultI++
                        rightI++
                        continue
                    } else {
                        // replace the current interval
                        result.intervals.set(resultI, beforeCurrent)
                        resultI++
                        continue
                    }
                } else {
                    if (afterCurrent != null) {
                        // replace the current interval
                        result.intervals.set(resultI, afterCurrent)
                        rightI++
                        continue
                    } else {
                        // remove the current interval (thus no need to increment resultI)
                        result.intervals.remove(resultI)
                        continue
                    }
                }
            }

            // If rightI reached right.intervals.size, no more intervals to subtract from result.
            // If resultI reached result.intervals.size, we would be subtracting from an empty set.
            // Either way, we are done.
            return result
        }
    }
}
