package org.antlr.v4.runtime.misc

import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.Vocabulary
import org.antlr.v4.runtime.VocabularyImpl

class IntervalSet : IntSet {
    internal var intervals: MutableList<Interval> = ArrayList()
    protected var readonly: Boolean = false

    constructor(intervals: MutableList<Interval>?) {
        this.intervals = intervals ?: ArrayList()
    }

    constructor(set: IntervalSet?) : this() {
        addAll(set)
    }

    constructor(vararg els: Int) {
        intervals = ArrayList<Interval>(2)
        for (e in els) add(e)
    }

    override fun clear() {
        check(!readonly) { "can't alter readonly IntervalSet" }
        intervals.clear()
    }

    fun add(el: Int) {
        check(!readonly) { "can't alter readonly IntervalSet" }
        add(el, el)
    }

    fun add(a: Int, b: Int) {
        add(Interval.of(a, b))
    }

    protected fun add(addition: Interval) {
        check(!readonly) { "can't alter readonly IntervalSet" }
        if (addition.b < addition.a) {
            return
        }
        val iter: ListIterator<Interval> = intervals.listIterator()
        while (iter.hasNext()) {
            val r: Interval? = iter.next()
            if (addition.equals(r)) {
                return
            }
            if (addition.adjacent(r) || !addition.disjoint(r)) {
                val bigger: Interval = addition.union(r)
                iter.set(bigger)
                while (iter.hasNext()) {
                    val next: Interval? = iter.next()
                    if (!bigger.adjacent(next) && bigger.disjoint(next)) {
                        break
                    }
                    iter.remove()
                    iter.previous()
                    iter.set(bigger.union(next))
                    iter.next()
                }
                return
            }
            if (addition.startsBeforeDisjoint(r)) {
                iter.previous()
                iter.add(addition)
                return
            }
        }
        intervals.add(addition)
    }

    override fun addAll(set: IntSet?): IntervalSet {
        if (set == null) {
            return this
        }
        if (set is IntervalSet) {
            val other = set
            val n: Int = other.intervals.size
            for (i in 0..<n) {
                val I: Interval = other.intervals[i]
                this.add(I.a, I.b)
            }
        } else {
            for (value in set.toList()) {
                if (value != null) add(value)
            }
        }
        return this
    }

    fun complement(minElement: Int, maxElement: Int): IntervalSet? =
        this.complement(IntervalSet.of(minElement, maxElement))

    override fun complement(vocabulary: IntSet?): IntervalSet? {
        if (vocabulary == null || vocabulary.isNil) {
            return null
        }
        val vocabularyIS: IntervalSet = if (vocabulary is IntervalSet) {
            vocabulary
        } else {
            IntervalSet().also { it.addAll(vocabulary) }
        }
        return vocabularyIS.subtract(this)
    }

    override fun subtract(a: IntSet?): IntervalSet {
        if (a == null || a.isNil) {
            return IntervalSet(this)
        }
        if (a is IntervalSet) {
            return IntervalSet.subtract(this, a)
        }
        val other: IntervalSet = IntervalSet()
        other.addAll(a)
        return IntervalSet.subtract(this, other)
    }

    override fun or(a: IntSet?): IntervalSet {
        val o: IntervalSet = IntervalSet()
        o.addAll(this)
        o.addAll(a)
        return o
    }

    override fun and(other: IntSet?): IntervalSet? {
        if (other == null) {
            return null
        }
        val myIntervals: MutableList<Interval> = this.intervals
        val theirIntervals: MutableList<Interval> = (other as IntervalSet).intervals
        var intersection: IntervalSet? = null
        val mySize: Int = myIntervals.size
        val theirSize: Int = theirIntervals.size
        var i = 0
        var j = 0
        while (i < mySize && j < theirSize) {
            val mine: Interval = myIntervals[i]
            val theirs: Interval = theirIntervals[j]
            if (mine.startsBeforeDisjoint(theirs)) {
                i++
            } else if (theirs.startsBeforeDisjoint(mine)) {
                j++
            } else if (mine.properlyContains(theirs)) {
                if (intersection == null) {
                    intersection = IntervalSet()
                }
                intersection.add(mine.intersection(theirs))
                j++
            } else if (theirs.properlyContains(mine)) {
                if (intersection == null) {
                    intersection = IntervalSet()
                }
                intersection.add(mine.intersection(theirs))
                i++
            } else if (!mine.disjoint(theirs)) {
                if (intersection == null) {
                    intersection = IntervalSet()
                }
                intersection.add(mine.intersection(theirs))
                if (mine.startsAfterNonDisjoint(theirs)) {
                    j++
                } else if (theirs.startsAfterNonDisjoint(mine)) {
                    i++
                }
            }
        }
        return intersection ?: IntervalSet()
    }

    override fun contains(el: Int): Boolean {
        val n: Int = intervals.size
        var l = 0
        var r = n - 1
        while (l <= r) {
            val m = (l + r) / 2
            val I: Interval = intervals[m]
            val a: Int = I.a
            val b: Int = I.b
            if (b < el) {
                l = m + 1
            } else if (a > el) {
                r = m - 1
            } else {
                return true
            }
        }
        return false
    }

    override val isNil: Boolean
        get() = intervals.isEmpty()

    val maxElement: Int
        get() {
            if (this.isNil) {
                throw RuntimeException("set is empty")
            }
            val last: Interval = intervals[intervals.size - 1]
            return last.b
        }

    val minElement: Int
        get() {
            if (this.isNil) {
                throw RuntimeException("set is empty")
            }
            return intervals[0].a
        }

    fun getIntervals(): MutableList<Interval> = intervals

    override fun hashCode(): Int {
        var hash: Int = MurmurHash.initialize()
        for (I in intervals) {
            hash = MurmurHash.update(hash, I.a)
            hash = MurmurHash.update(hash, I.b)
        }
        hash = MurmurHash.finish(hash, intervals.size * 2)
        return hash
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null || obj !is IntervalSet) {
            return false
        }
        val other = obj
        return this.intervals == other.intervals
    }

    override fun toString(): String? = toString(false)

    fun toString(elemAreChar: Boolean): String? {
        val buf: StringBuilder = StringBuilder()
        if (this.intervals.isEmpty()) {
            return "{}"
        }
        if (this.size() > 1) {
            buf.append("{")
        }
        val iter: Iterator<Interval> = this.intervals.iterator()
        while (iter.hasNext()) {
            val I: Interval = iter.next()
            val a: Int = I.a
            val b: Int = I.b
            if (a == b) {
                if (a == Token.EOF) {
                    buf.append("<EOF>")
                } else if (elemAreChar) {
                    buf.append("'").appendCodePoint(a).append("'")
                } else {
                    buf.append(a)
                }
            } else {
                if (elemAreChar) {
                    buf.append("'").appendCodePoint(a).append("'..'").appendCodePoint(b).append("'")
                } else {
                    buf.append(a).append("..").append(b)
                }
            }
            if (iter.hasNext()) {
                buf.append(", ")
            }
        }
        if (this.size() > 1) {
            buf.append("}")
        }
        return buf.toString()
    }

    @Deprecated("Use {@link #toString(Vocabulary)} instead.")
    fun toString(tokenNames: Array<String?>?): String? = toString(VocabularyImpl.fromTokenNames(tokenNames))

    fun toString(vocabulary: Vocabulary): String? {
        val buf: StringBuilder = StringBuilder()
        if (this.intervals.isEmpty()) {
            return "{}"
        }
        if (this.size() > 1) {
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
        if (this.size() > 1) {
            buf.append("}")
        }
        return buf.toString()
    }

    @Deprecated("Use {@link #elementName(Vocabulary, int)} instead.")
    protected fun elementName(tokenNames: Array<String?>?, a: Int): String? =
        elementName(VocabularyImpl.fromTokenNames(tokenNames), a)

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
            val firstInterval: Interval = this.intervals[0]
            return firstInterval.b - firstInterval.a + 1
        }
        for (i in 0..<numIntervals) {
            val I: Interval = intervals[i]
            n += (I.b - I.a + 1)
        }
        return n
    }

    fun toIntList(): IntList {
        val values: IntList = IntList(size())
        val n: Int = intervals.size
        for (i in 0..<n) {
            val I: Interval = intervals[i]
            val a: Int = I.a
            val b: Int = I.b
            for (v in a..b) {
                values.add(v)
            }
        }
        return values
    }

    override fun toList(): List<Int> {
        val values: MutableList<Int> = ArrayList()
        val n: Int = intervals.size
        for (i in 0..<n) {
            val I: Interval = intervals[i]
            val a: Int = I.a
            val b: Int = I.b
            for (v in a..b) {
                values.add(v)
            }
        }
        return values
    }

    fun toSet(): Set<Int> {
        val s: MutableSet<Int> = HashSet()
        for (I in intervals) {
            val a: Int = I.a
            val b: Int = I.b
            for (v in a..b) {
                s.add(v)
            }
        }
        return s
    }

    fun get(i: Int): Int {
        val n: Int = intervals.size
        var index = 0
        for (j in 0..<n) {
            val I: Interval = intervals[j]
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

    fun toArray(): IntArray = toIntList().toArray()

    override fun remove(el: Int) {
        check(!readonly) { "can't alter readonly IntervalSet" }
        val n: Int = intervals.size
        for (i in 0..<n) {
            var I: Interval = intervals[i]
            val a: Int = I.a
            val b: Int = I.b
            if (el < a) {
                break
            }
            if (el == a && el == b) {
                intervals.removeAt(i)
                break
            }
            if (el == a) {
                I = Interval.of(a + 1, b)
                intervals[i] = I
                break
            }
            if (el == b) {
                I = Interval.of(a, b - 1)
                intervals[i] = I
                break
            }
            if (el > a && el < b) {
                val oldb: Int = I.b
                I = Interval.of(a, el - 1)
                intervals[i] = I
                add(el + 1, oldb)
            }
        }
    }

    fun isReadonly(): Boolean = readonly

    fun setReadonly(readonly: Boolean) {
        check(!(this.readonly && !readonly)) { "can't alter readonly IntervalSet" }
        this.readonly = readonly
    }

    companion object {
        val COMPLETE_CHAR_SET: IntervalSet = IntervalSet.of(Lexer.MIN_CHAR_VALUE, Lexer.MAX_CHAR_VALUE)

        init {
            COMPLETE_CHAR_SET.setReadonly(true)
        }

        val EMPTY_SET: IntervalSet = IntervalSet()

        init {
            EMPTY_SET.setReadonly(true)
        }

        fun of(a: Int): IntervalSet {
            val s: IntervalSet = IntervalSet()
            s.add(a)
            return s
        }

        fun of(a: Int, b: Int): IntervalSet {
            val s: IntervalSet = IntervalSet()
            s.add(a, b)
            return s
        }

        fun or(sets: Array<IntervalSet?>): IntervalSet {
            val r: IntervalSet = IntervalSet()
            for (s in sets) r.addAll(s)
            return r
        }

        fun subtract(left: IntervalSet?, right: IntervalSet?): IntervalSet {
            if (left == null || left.isNil) {
                return IntervalSet()
            }
            val result: IntervalSet = IntervalSet(left)
            if (right == null || right.isNil) {
                return result
            }
            var resultI = 0
            var rightI = 0
            while (resultI < result.intervals.size && rightI < right.intervals.size) {
                val resultInterval: Interval = result.intervals[resultI]
                val rightInterval: Interval = right.intervals[rightI]
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
                        result.intervals[resultI] = beforeCurrent
                        result.intervals.add(resultI + 1, afterCurrent)
                        resultI++
                        rightI++
                        continue
                    } else {
                        result.intervals[resultI] = beforeCurrent
                        resultI++
                        continue
                    }
                } else {
                    if (afterCurrent != null) {
                        result.intervals[resultI] = afterCurrent
                        rightI++
                        continue
                    } else {
                        result.intervals.removeAt(resultI)
                        continue
                    }
                }
            }
            return result
        }
    }
}
