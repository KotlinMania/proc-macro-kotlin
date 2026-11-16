// port-lint: source src/bridge/fxhash.rs
package io.github.kotlinmania.procmacro.bridge

internal const val FX_HASH_K32: UInt = 0x9e3779b9u
internal const val FX_HASH_K64: ULong = 0x517cc1b727220a95uL

/**
 * Fast non-cryptographic 64-bit word-at-a-time hasher used by the bridge
 * symbol interner and handle stores.
 */
internal class FxHasher(
    internal var hash: ULong = 0uL,
) {
    fun addToHash(word: ULong) {
        hash = (hash.rotateLeft(5) xor word) * FX_HASH_K64
    }

    fun write(bytes: ByteArray) {
        var index = 0
        val limit8 = bytes.size - (bytes.size % 8)
        while (index < limit8) {
            addToHash(readLeU64(bytes, index))
            index += 8
        }
        if (bytes.size - index >= 4) {
            addToHash(readLeU32(bytes, index).toULong())
            index += 4
        }
        if (bytes.size - index >= 2) {
            addToHash(readLeU16(bytes, index).toULong())
            index += 2
        }
        if (index < bytes.size) {
            addToHash((bytes[index].toInt() and 0xff).toULong())
        }
    }

    fun writeU8(value: UByte) {
        addToHash(value.toULong())
    }

    fun writeU16(value: UShort) {
        addToHash(value.toULong())
    }

    fun writeU32(value: UInt) {
        addToHash(value.toULong())
    }

    fun writeU64(value: ULong) {
        addToHash(value)
    }

    fun writeUsize(value: Int) {
        addToHash(value.toUInt().toULong())
    }

    fun writeString(value: String) {
        write(value.encodeToByteArray())
        writeU8(0xffu)
    }

    fun finish(): ULong = hash

    companion object {
        fun default(): FxHasher = FxHasher(0uL)

        fun hashBytes(bytes: ByteArray): ULong =
            FxHasher().also { it.write(bytes) }.finish()

        fun hashString(value: String): ULong =
            FxHasher().also { it.writeString(value) }.finish()
    }
}

/**
 * Hash map keyed with [FxHasher] for bridge symbol and handle interning.
 */
internal class FxHashMap<K, V> {
    private class Entry<K, V>(
        val key: K,
        var value: V,
        val fxHash: ULong,
        var next: Entry<K, V>? = null,
    )

    private var buckets: Array<Entry<K, V>?> = arrayOfNulls(16)
    private var entryCount: Int = 0

    val size: Int
        get() = entryCount

    fun isEmpty(): Boolean = entryCount == 0

    operator fun get(key: K): V? {
        val fx = computeKeyHash(key)
        var node = buckets[bucketIndex(fx, buckets.size)]
        while (node != null) {
            if (node.fxHash == fx && node.key == key) {
                return node.value
            }
            node = node.next
        }
        return null
    }

    fun containsKey(key: K): Boolean = get(key) != null

    fun insert(
        key: K,
        value: V,
    ): V? {
        if (entryCount + 1 > (buckets.size * 3) / 4) {
            rehash(buckets.size * 2)
        }
        val fx = computeKeyHash(key)
        val idx = bucketIndex(fx, buckets.size)
        var node = buckets[idx]
        while (node != null) {
            if (node.fxHash == fx && node.key == key) {
                val previous = node.value
                node.value = value
                return previous
            }
            node = node.next
        }
        buckets[idx] = Entry(key = key, value = value, fxHash = fx, next = buckets[idx])
        entryCount++
        return null
    }

    operator fun set(
        key: K,
        value: V,
    ) {
        insert(key, value)
    }

    fun getOrPut(
        key: K,
        defaultValue: () -> V,
    ): V {
        val existing = get(key)
        if (existing != null) return existing
        val computed = defaultValue()
        insert(key, computed)
        return computed
    }

    fun remove(key: K): V? {
        val fx = computeKeyHash(key)
        val idx = bucketIndex(fx, buckets.size)
        var prev: Entry<K, V>? = null
        var node = buckets[idx]
        while (node != null) {
            if (node.fxHash == fx && node.key == key) {
                if (prev == null) {
                    buckets[idx] = node.next
                } else {
                    prev.next = node.next
                }
                entryCount--
                return node.value
            }
            prev = node
            node = node.next
        }
        return null
    }

    fun clear() {
        buckets = arrayOfNulls(16)
        entryCount = 0
    }

    private fun rehash(newCapacity: Int) {
        val nextBuckets = arrayOfNulls<Entry<K, V>>(newCapacity)
        for (head in buckets) {
            var node = head
            while (node != null) {
                val next = node.next
                val idx = bucketIndex(node.fxHash, newCapacity)
                node.next = nextBuckets[idx]
                nextBuckets[idx] = node
                node = next
            }
        }
        buckets = nextBuckets
    }

    private fun bucketIndex(
        fx: ULong,
        capacity: Int,
    ): Int = (fx.toInt() xor (fx shr 32).toInt()) and (capacity - 1)

    private fun computeKeyHash(key: K): ULong {
        val hasher = FxHasher()
        when (key) {
            null -> hasher.writeU8(0u)
            is String -> hasher.writeString(key)
            is ByteArray -> hasher.write(key)
            is Int -> hasher.writeU32(key.toUInt())
            is UInt -> hasher.writeU32(key)
            is Long -> hasher.writeU64(key.toULong())
            is ULong -> hasher.writeU64(key)
            else -> hasher.writeU32(key.hashCode().toUInt())
        }
        return hasher.finish()
    }

    companion object {
        fun <K, V> default(): FxHashMap<K, V> = FxHashMap()
    }
}

private fun readLeU16(
    bytes: ByteArray,
    offset: Int,
): UShort {
    val b0 = bytes[offset].toInt() and 0xff
    val b1 = bytes[offset + 1].toInt() and 0xff
    return (b0 or (b1 shl 8)).toUShort()
}

private fun readLeU32(
    bytes: ByteArray,
    offset: Int,
): UInt {
    val b0 = (bytes[offset].toInt() and 0xff).toUInt()
    val b1 = (bytes[offset + 1].toInt() and 0xff).toUInt()
    val b2 = (bytes[offset + 2].toInt() and 0xff).toUInt()
    val b3 = (bytes[offset + 3].toInt() and 0xff).toUInt()
    return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
}

private fun readLeU64(
    bytes: ByteArray,
    offset: Int,
): ULong {
    val lo = readLeU32(bytes, offset).toULong()
    val hi = readLeU32(bytes, offset + 4).toULong()
    return lo or (hi shl 32)
}
