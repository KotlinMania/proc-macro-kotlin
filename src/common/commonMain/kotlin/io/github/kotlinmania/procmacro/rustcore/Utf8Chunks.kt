package io.github.kotlinmania.procmacro.rustcore

internal data class Utf8Chunk(
    val valid: String,
    val invalid: ByteArray,
)

/**
 * Walks [bytes] and yields runs of well-formed UTF-8 decoded into a [String]
 * interleaved with any invalid byte runs that terminated them.
 */
internal fun utf8Chunks(bytes: ByteArray): Sequence<Utf8Chunk> =
    sequence {
        var i = 0
        while (i < bytes.size) {
            val validStart = i
            while (i < bytes.size) {
                val len = utf8SequenceLength(bytes, i)
                if (len == 0) break
                i += len
            }
            val valid = if (i == validStart) "" else bytes.decodeToString(validStart, i)
            val invalidStart = i
            while (i < bytes.size && utf8SequenceLength(bytes, i) == 0) {
                i += 1
            }
            val invalid = bytes.copyOfRange(invalidStart, i)
            if (valid.isEmpty() && invalid.isEmpty()) break
            yield(Utf8Chunk(valid, invalid))
        }
    }

/**
 * Returns the byte length of the well-formed UTF-8 sequence starting at
 * [offset] inside [bytes], or `0` when the byte at [offset] does not start
 * one. Rejects overlong encodings and surrogate code points.
 */
internal fun utf8SequenceLength(
    bytes: ByteArray,
    offset: Int,
): Int {
    val b0 = bytes[offset].toInt() and 0xff
    return when {
        b0 < 0x80 -> 1
        b0 < 0xc2 -> 0
        b0 < 0xe0 -> {
            if (offset + 1 >= bytes.size) {
                0
            } else if (!isUtf8Continuation(bytes[offset + 1])) {
                0
            } else {
                2
            }
        }
        b0 < 0xf0 -> {
            if (offset + 2 >= bytes.size) return 0
            val b1 = bytes[offset + 1].toInt() and 0xff
            val b2 = bytes[offset + 2].toInt() and 0xff
            val b1Lo =
                when (b0) {
                    0xe0 -> 0xa0
                    else -> 0x80
                }
            val b1Hi =
                when (b0) {
                    0xed -> 0x9f
                    else -> 0xbf
                }
            if (b1 !in b1Lo..b1Hi) {
                0
            } else if (b2 !in 0x80..0xbf) {
                0
            } else {
                3
            }
        }
        b0 < 0xf5 -> {
            if (offset + 3 >= bytes.size) return 0
            val b1 = bytes[offset + 1].toInt() and 0xff
            val b2 = bytes[offset + 2].toInt() and 0xff
            val b3 = bytes[offset + 3].toInt() and 0xff
            val b1Lo =
                when (b0) {
                    0xf0 -> 0x90
                    else -> 0x80
                }
            val b1Hi =
                when (b0) {
                    0xf4 -> 0x8f
                    else -> 0xbf
                }
            if (b1 !in b1Lo..b1Hi) {
                0
            } else if (b2 !in 0x80..0xbf) {
                0
            } else if (b3 !in 0x80..0xbf) {
                0
            } else {
                4
            }
        }
        else -> 0
    }
}

internal fun isUtf8Continuation(byte: Byte): Boolean {
    val u = byte.toInt() and 0xff
    return u in 0x80..0xbf
}

/**
 * Iterates 21-bit Unicode scalar values out of [s], joining UTF-16 surrogate
 * pairs into supplementary-plane code points.
 */
internal fun unicodeScalars(s: String): Sequence<Int> =
    sequence {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) {
                val low = s[i + 1]
                val cp = 0x10000 + ((c.code - 0xd800) shl 10) + (low.code - 0xdc00)
                yield(cp)
                i += 2
            } else {
                yield(c.code)
                i += 1
            }
        }
    }

/**
 * Appends a 21-bit Unicode scalar value to a [StringBuilder], emitting a
 * UTF-16 surrogate pair for supplementary-plane code points.
 */
internal fun StringBuilder.appendUnicodeScalar(codePoint: Int): StringBuilder {
    if (codePoint <= 0xffff) {
        append(codePoint.toChar())
    } else {
        val offset = codePoint - 0x10000
        append((0xd800 + (offset shr 10)).toChar())
        append((0xdc00 + (offset and 0x3ff)).toChar())
    }
    return this
}
