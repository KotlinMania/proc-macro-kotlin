// port-lint: source src/bridge/buffer.rs
package io.github.kotlinmania.procmacro.bridge

import kotlin.math.max

internal fun interface BufferReserveCallback {
    fun invoke(
        buffer: Buffer,
        additional: Int,
    ): Buffer
}

internal fun interface BufferDropCallback {
    fun invoke(buffer: Buffer)
}

/**
 * Growable byte buffer for same-process client and server RPC serialization.
 */
internal class Buffer internal constructor(
    internal var data: ByteArray,
    internal var len: Int,
    internal var capacity: Int,
    internal var reserve: BufferReserveCallback,
    internal var dropCallback: BufferDropCallback,
) {
    val size: Int
        get() = len

    fun isEmpty(): Boolean = len == 0

    fun clear() {
        len = 0
    }

    fun take(): Buffer {
        val taken =
            Buffer(
                data = data,
                len = len,
                capacity = capacity,
                reserve = reserve,
                dropCallback = dropCallback,
            )
        val fresh = default()
        data = fresh.data
        len = fresh.len
        capacity = fresh.capacity
        reserve = fresh.reserve
        dropCallback = fresh.dropCallback
        return taken
    }

    fun extendFromArray(xs: ByteArray) {
        ensureAdditionalCapacity(xs.size)
        xs.copyInto(data, destinationOffset = len, startIndex = 0, endIndex = xs.size)
        len += xs.size
    }

    fun extendFromSlice(xs: ByteArray) {
        extendFromArray(xs)
    }

    fun push(v: UByte) {
        ensureAdditionalCapacity(1)
        data[len] = v.toByte()
        len += 1
    }

    fun pushByte(v: Byte) {
        ensureAdditionalCapacity(1)
        data[len] = v
        len += 1
    }

    fun write(xs: ByteArray): Int {
        extendFromSlice(xs)
        return xs.size
    }

    fun writeAll(xs: ByteArray) {
        extendFromSlice(xs)
    }

    fun flush() {
        // In-memory buffer requires no external flush.
    }

    fun drop() {
        dropCallback.invoke(this)
    }

    fun byteAt(index: Int): UByte {
        require(index in 0 until len) { "Buffer index $index out of bounds for length $len" }
        return data[index].toUByte()
    }

    fun toByteArray(): ByteArray = data.copyOfRange(0, len)

    fun toReader(): BufferReader = BufferReader(data, 0, len)

    private fun ensureAdditionalCapacity(additional: Int) {
        if (additional > capacity - len) {
            val grown = reserve.invoke(this, additional)
            data = grown.data
            len = grown.len
            capacity = grown.capacity
            reserve = grown.reserve
            dropCallback = grown.dropCallback
        }
    }

    companion object {
        private val defaultReserve =
            BufferReserveCallback { buf, additional ->
                val required = buf.len + additional
                val doubled = if (buf.capacity == 0) 64 else buf.capacity * 2
                val nextCapacity = max(required, doubled)
                val nextData = buf.data.copyOf(nextCapacity)
                Buffer(
                    data = nextData,
                    len = buf.len,
                    capacity = nextCapacity,
                    reserve = buf.reserve,
                    dropCallback = buf.dropCallback,
                )
            }

        private val defaultDrop =
            BufferDropCallback { buf ->
                buf.data = ByteArray(0)
                buf.len = 0
                buf.capacity = 0
            }

        fun default(): Buffer = from(ByteArray(0))

        fun new(): Buffer = default()

        fun from(bytes: ByteArray): Buffer =
            Buffer(
                data = bytes.copyOf(),
                len = bytes.size,
                capacity = bytes.size,
                reserve = defaultReserve,
                dropCallback = defaultDrop,
            )
    }
}

/**
 * Forward cursor over serialized bytes inside a [Buffer] or [ByteArray].
 */
internal class BufferReader(
    private val bytes: ByteArray,
    private var position: Int = 0,
    private val limit: Int = bytes.size,
) {
    val remaining: Int
        get() = limit - position

    fun isEmpty(): Boolean = position >= limit

    fun readUByte(): UByte {
        check(position < limit) { "unexpected end of RPC buffer" }
        return bytes[position++].toUByte()
    }

    fun readBytes(count: Int): ByteArray {
        require(count >= 0) { "negative RPC read length: $count" }
        check(position + count <= limit) {
            "unexpected end of RPC buffer: requested $count bytes with $remaining remaining"
        }
        val slice = bytes.copyOfRange(position, position + count)
        position += count
        return slice
    }
}
