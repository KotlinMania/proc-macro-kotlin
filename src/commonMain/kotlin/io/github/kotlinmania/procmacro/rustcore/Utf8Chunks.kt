// port-lint: ignore (Rust core stdlib emulation; no upstream under tmp/proc-macro)
package io.github.kotlinmania.procmacro.rustcore

/**
 * Kotlin emulation of pieces of Rust `core` that escape.rs depends on
 * but that Kotlin's stdlib does not directly expose. None of this
 * mirrors a file under `tmp/proc-macro/`; the upstream references are
 * the corresponding items in `rust-lang/rust:library/core/`.
 *
 * Items covered here, with their Rust counterparts:
 *
 * | Kotlin               | Rust                                             |
 * |----------------------|--------------------------------------------------|
 * | `Utf8Chunk`          | `core::str::Utf8Chunk`                           |
 * | `utf8Chunks`         | `<[u8]>::utf8_chunks` via `core::str::Utf8Chunks`|
 * | `unicodeScalars`     | `str::chars` (yielded as scalar values)          |
 * | `appendCodePoint`    | `String::push(char)` for supplementary planes    |
 *
 * These belong in a future `core-kotlin` sibling if one is ever
 * created. They live here for now because proc-macro-kotlin is the
 * first consumer; until then their visibility is `internal` so they
 * are not part of the proc-macro public surface.
 */

internal data class Utf8Chunk(
    val valid: String,
    val invalid: ByteArray,
)

/**
 * Walks `bytes` and yields runs of valid UTF-8 (decoded into a Kotlin
 * `String`) interleaved with the invalid byte sequence that terminated
 * each run, mirroring Rust's `<[u8]>::utf8_chunks()`.
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
 * Returns the length of the well-formed UTF-8 sequence starting at
 * [offset] inside [bytes], or `0` when the byte at [offset] does not
 * begin one. Follows the Unicode 15.0 well-formed UTF-8 table, which
 * forbids overlong encodings and surrogate code points.
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
 * Iterates Unicode scalar values out of a Kotlin `String`, joining
 * surrogate pairs into supplementary-plane code points. Equivalent to
 * Rust's `str::chars` over `&str`.
 *
 * Declared as a top-level function rather than an extension because
 * `java.lang.CharSequence.codePoints(): IntStream` shadows extensions
 * named `codePoints` on the JVM/Android targets.
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
 * Appends a Unicode scalar value to a `StringBuilder`, emitting a
 * surrogate pair for supplementary-plane code points. Equivalent to
 * Rust's `String::push(char)`.
 */
internal fun StringBuilder.appendCodePoint(codePoint: Int): StringBuilder {
    if (codePoint <= 0xffff) {
        append(codePoint.toChar())
    } else {
        val offset = codePoint - 0x10000
        append((0xd800 + (offset shr 10)).toChar())
        append((0xdc00 + (offset and 0x3ff)).toChar())
    }
    return this
}
