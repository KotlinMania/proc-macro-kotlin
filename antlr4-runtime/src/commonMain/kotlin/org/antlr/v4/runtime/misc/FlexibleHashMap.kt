/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc

import kotlin.math.floor

class FlexibleHashMap<K, V>
    @kotlin.jvm.JvmOverloads
    constructor(
        comparator: AbstractEqualityComparator<in K> = AnyEqualityComparator.INSTANCE as AbstractEqualityComparator<in K>,
        initialCapacity: Int = INITIAL_CAPACITY,
        initialBucketCapacity: Int = INITIAL_BUCKET_CAPACITY,
    ) : MutableMap<K, V> {
        class Entry<K, V>(
            override val key: K,
            override var value: V,
        ) : MutableMap.MutableEntry<K, V> {
            override fun toString(): String = "$key:$value"
        }

        protected val comparator: AbstractEqualityComparator<in K>

        protected var buckets: Array<ArrayDeque<Entry<K, V>>?>

        protected var n: Int = 0

        protected var currentPrime: Int = 1

        protected var threshold: Int
        protected val initialCapacity: Int
        protected val initialBucketCapacity: Int

        init {
            this.comparator = comparator
            this.initialCapacity = initialCapacity
            this.initialBucketCapacity = initialBucketCapacity
            this.threshold = floor(initialCapacity * LOAD_FACTOR).toInt()
            this.buckets = createEntryListArray(initialBucketCapacity)
        }

        protected fun getBucket(key: K): Int {
            val hash = comparator.hashCode(key)
            return hash and (buckets.size - 1)
        }

        override fun get(key: Any?): V? {
            @Suppress("UNCHECKED_CAST")
            val typedKey = key as? K ?: return null
            val b = getBucket(typedKey)
            val bucket = buckets[b] ?: return null
            for (e in bucket) {
                if (comparator.equals(e.key, typedKey)) return e.value
            }
            return null
        }

        override fun put(
            key: K,
            value: V,
        ): V? {
            if (n > threshold) expand()
            val b = getBucket(key)
            var bucket = buckets[b]
            if (bucket == null) {
                bucket = ArrayDeque()
                buckets[b] = bucket
            }
            for (e in bucket) {
                if (comparator.equals(e.key, key)) {
                    val prev = e.value
                    e.value = value
                    n++
                    return prev
                }
            }
            bucket.add(Entry(key, value))
            n++
            return null
        }

        override fun remove(key: Any?): V? = throw UnsupportedOperationException()

        override fun putAll(from: Map<out K, V>) = throw UnsupportedOperationException()

        override val keys: MutableSet<K>
            get() = throw UnsupportedOperationException()

        override val values: Collection<V>
            get() {
                val a = mutableListOf<V>()
                for (bucket in buckets) {
                    if (bucket == null) continue
                    for (e in bucket) a.add(e.value)
                }
                return a
            }

        override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
            get() = throw UnsupportedOperationException()

        override fun containsKey(key: Any): Boolean = get(key) != null

        override fun containsValue(value: V): Boolean = throw UnsupportedOperationException()

        override fun hashCode(): Int {
            var hash = MurmurHash.initialize()
            for (bucket in buckets) {
                if (bucket == null) continue
                for (e in bucket) {
                    hash = MurmurHash.update(hash, comparator.hashCode(e.key))
                }
            }
            hash = MurmurHash.finish(hash, size)
            return hash
        }

        override fun equals(other: Any?): Boolean = throw UnsupportedOperationException()

        protected fun expand() {
            val old = buckets
            currentPrime += 4
            val newCapacity = buckets.size * 2
            val newTable = createEntryListArray(newCapacity)
            buckets = newTable
            threshold = floor(newCapacity * LOAD_FACTOR).toInt()
            val oldSize = n
            for (bucket in old) {
                if (bucket == null) continue
                for (e in bucket) put(e.key, e.value)
            }
            n = oldSize
        }

        override val size: Int
            get() = n

        override fun isEmpty(): Boolean = n == 0

        override fun clear() {
            buckets = createEntryListArray(initialCapacity)
            n = 0
            threshold = floor(initialCapacity * LOAD_FACTOR).toInt()
        }

        override fun toString(): String {
            if (size == 0) return "{}"
            val buf = StringBuilder()
            buf.append('{')
            var first = true
            for (bucket in buckets) {
                if (bucket == null) continue
                for (e in bucket) {
                    if (first) first = false else buf.append(", ")
                    buf.append(e.toString())
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
                for (e in bucket) {
                    if (first) first = false else buf.append(" ")
                    buf.append(e.toString())
                }
                buf.append("]\n")
            }
            return buf.toString()
        }

        companion object {
            const val INITIAL_CAPACITY: Int = 16
            const val INITIAL_BUCKET_CAPACITY: Int = 8
            const val LOAD_FACTOR: Double = 0.75

            @Suppress("UNCHECKED_CAST")
            private fun <K, V> createEntryListArray(length: Int): Array<ArrayDeque<Entry<K, V>>?> =
                arrayOfNulls<ArrayDeque<*>>(length) as Array<ArrayDeque<Entry<K, V>>?>
        }
    }
