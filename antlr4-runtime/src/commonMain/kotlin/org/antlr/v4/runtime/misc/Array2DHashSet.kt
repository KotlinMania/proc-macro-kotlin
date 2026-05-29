/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc

import kotlin.math.floor

open class Array2DHashSet<T>
    @kotlin.jvm.JvmOverloads
    constructor(
        comparator: AbstractEqualityComparator<in T> = AnyEqualityComparator.INSTANCE as AbstractEqualityComparator<in T>,
        initialCapacity: Int = INITIAL_CAPACITY,
        initialBucketCapacity: Int = INITIAL_BUCKET_CAPACITY,
    ) : MutableSet<T> {
        protected val comparator: AbstractEqualityComparator<in T>

        protected var buckets: Array<Array<T?>?>

        protected var n: Int = 0

        protected var currentPrime: Int = 1
        protected var threshold: Int
        protected val initialCapacity: Int
        protected val initialBucketCapacity: Int

        init {
            this.comparator =
                if (comparator is AnyEqualityComparator && this !is AnyEqualityComparator) {
                    AnyEqualityComparator.INSTANCE as AbstractEqualityComparator<in T>
                } else {
                    comparator
                }
            this.initialCapacity = initialCapacity
            this.initialBucketCapacity = initialBucketCapacity
            this.buckets = createBuckets(initialCapacity)
            this.threshold = floor(initialCapacity * LOAD_FACTOR).toInt()
        }

        fun getOrAdd(o: T): T {
            if (n > threshold) expand()
            return getOrAddImpl(o)
        }

        protected fun getOrAddImpl(o: T): T {
            val b = getBucket(o)
            var bucket = buckets[b]

            if (bucket == null) {
                bucket = createBucket(initialBucketCapacity)
                bucket[0] = o
                buckets[b] = bucket
                n++
                return o
            }

            for (i in bucket.indices) {
                val existing = bucket[i]
                if (existing == null) {
                    bucket[i] = o
                    n++
                    return o
                }
                if (comparator.equals(existing, o)) return existing
            }

            val oldLength = bucket.size
            bucket = bucket.copyOf(bucket.size * 2)
            buckets[b] = bucket
            bucket[oldLength] = o
            n++
            return o
        }

        fun get(o: T): T? {
            if (o == null) return o
            val b = getBucket(o)
            val bucket = buckets[b] ?: return null

            for (e in bucket) {
                if (e == null) return null
                if (comparator.equals(e, o)) return e
            }
            return null
        }

        protected fun getBucket(o: T): Int {
            val hash = comparator.hashCode(o)
            return hash and (buckets.size - 1)
        }

        override fun hashCode(): Int {
            var hash = MurmurHash.initialize()
            for (bucket in buckets) {
                if (bucket == null) continue
                for (o in bucket) {
                    if (o == null) break
                    hash = MurmurHash.update(hash, comparator.hashCode(o))
                }
            }
            hash = MurmurHash.finish(hash, size)
            return hash
        }

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is Array2DHashSet<*>) return false
            if (other.size != size) return false
            return this.containsAll(other)
        }

        protected fun expand() {
            val old = buckets
            currentPrime += 4
            val newCapacity = buckets.size * 2
            val newTable = createBuckets(newCapacity)
            buckets = newTable
            threshold = floor(newCapacity * LOAD_FACTOR).toInt()

            for (bucket in old) {
                if (bucket == null) continue
                for (o in bucket) {
                    if (o == null) break
                    val b = getBucket(o)
                    var newBucket = newTable[b]
                    if (newBucket == null) {
                        newBucket = createBucket(initialBucketCapacity)
                        newTable[b] = newBucket
                    }
                    var i = 0
                    while (i < newBucket.size && newBucket[i] != null) i++
                    if (i >= newBucket.size) {
                        newBucket = newBucket.copyOf(newBucket.size * 2)
                        newTable[b] = newBucket
                    }
                    newBucket[i] = o
                }
            }
        }

        override fun add(element: T): Boolean {
            val existing = getOrAdd(element)
            return existing === element
        }

        override fun addAll(elements: Collection<T>): Boolean {
            var changed = false
            for (o in elements) {
                val existing = getOrAdd(o)
                if (existing != o) changed = true
            }
            return changed
        }

        fun containsFast(obj: T): Boolean = contains(obj)

        override fun contains(element: T): Boolean = get(element) != null

        override fun containsAll(elements: Collection<T>): Boolean {
            for (o in elements) {
                if (!containsFast(asElementType(o) ?: return false)) return false
            }
            return true
        }

        override fun remove(element: T): Boolean = removeFast(element)

        fun removeFast(obj: T): Boolean {
            val b = getBucket(obj)
            val bucket = buckets[b] ?: return false

            for (i in bucket.indices) {
                val e = bucket[i] ?: break
                if (comparator.equals(e, obj)) {
                    // shift rest down
                    val lastNonNil = bucket.indexOfFirst { it == null }.let { if (it < 0) bucket.size else it }
                    bucket[i] = bucket[lastNonNil - 1]
                    bucket[lastNonNil - 1] = null
                    n--
                    return true
                }
            }
            return false
        }

        override fun removeAll(elements: Collection<T>): Boolean {
            var changed = false
            for (o in elements) {
                changed = changed or removeFast(asElementType(o) ?: continue)
            }
            return changed
        }

        override fun retainAll(elements: Collection<T>): Boolean {
            var newsize = 0
            for (bucket in buckets) {
                if (bucket == null) continue
                var i = 0
                var j = 0
                while (i < bucket.size) {
                    if (bucket[i] == null) break
                    if (elements.contains(bucket[i])) {
                        if (i != j) bucket[j] = bucket[i]
                        j++
                        newsize++
                    }
                    i++
                }
                while (j < i) {
                    bucket[j] = null
                    j++
                }
            }
            val changed = newsize != n
            n = newsize
            return changed
        }

        override fun clear() {
            n = 0
            buckets = createBuckets(initialCapacity)
            threshold = floor(initialCapacity * LOAD_FACTOR).toInt()
        }

        override fun toString(): String {
            if (size == 0) return "{}"
            val buf = StringBuilder()
            buf.append('{')
            var first = true
            for (bucket in buckets) {
                if (bucket == null) continue
                for (o in bucket) {
                    if (o == null) break
                    if (first) first = false else buf.append(", ")
                    buf.append(o.toString())
                }
            }
            buf.append('}')
            return buf.toString()
        }

        fun toTableString(): String {
            val buf = StringBuilder()
            for (bucket in buckets) {
                if (bucket == null) {
                    buf.append("null\n")
                    continue
                }
                buf.append('[')
                var first = true
                for (o in bucket) {
                    if (first) first = false else buf.append(" ")
                    if (o == null) buf.append("_") else buf.append(o.toString())
                }
                buf.append("]\n")
            }
            return buf.toString()
        }

        @Suppress("UNCHECKED_CAST")
        protected open fun asElementType(o: Any?): T? = o as? T

        @Suppress("UNCHECKED_CAST")
        protected open fun createBuckets(capacity: Int): Array<Array<T?>?> = arrayOfNulls<Array<Any?>>(capacity) as Array<Array<T?>?>

        @Suppress("UNCHECKED_CAST")
        protected open fun createBucket(capacity: Int): Array<T?> = arrayOfNulls<Any>(capacity) as Array<T?>

        override val size: Int
            get() = n

        override fun isEmpty(): Boolean = n == 0

        override fun iterator(): MutableIterator<T> = SetIterator(toArray())

        fun toArray(): Array<T> {
            val a = arrayOfNulls<Any>(size)
            var i = 0
            for (bucket in buckets) {
                if (bucket == null) continue
                for (o in bucket) {
                    if (o == null) break
                    a[i++] = o
                }
            }
            @Suppress("UNCHECKED_CAST")
            return a as Array<T>
        }

        private inner class SetIterator(
            val data: Array<T>,
        ) : MutableIterator<T> {
            var nextIndex: Int = 0
            var removed: Boolean = true

            override fun hasNext(): Boolean = nextIndex < data.size

            override fun next(): T {
                if (!hasNext()) throw NoSuchElementException()
                removed = false
                return data[nextIndex++]
            }

            override fun remove() {
                check(!removed)
                this@Array2DHashSet.remove(data[nextIndex - 1])
                removed = true
            }
        }

        companion object {
            const val INITIAL_CAPACITY: Int = 16
            const val INITIAL_BUCKET_CAPACITY: Int = 8
            const val LOAD_FACTOR: Double = 0.75
        }
    }
