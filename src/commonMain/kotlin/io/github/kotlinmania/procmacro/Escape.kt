// port-lint: source src/escape.rs
package io.github.kotlinmania.procmacro

internal data class EscapeOptions(
    /** Produce `\'`. */
    val escapeSingleQuote: Boolean,
    /** Produce `\"`. */
    val escapeDoubleQuote: Boolean,
    /**
     * Produce `\x` escapes for non-ASCII, and use `\x` rather than `\u`
     * for ASCII control characters.
     */
    val escapeNonAscii: Boolean,
)

internal fun escapeBytes(bytes: ByteArray, opt: EscapeOptions): String {
    val repr = StringBuilder()

    if (opt.escapeNonAscii) {
        for (byte in bytes) {
            escapeSingleByte(byte, opt, repr)
        }
    } else {
        for (chunk in utf8Chunks(bytes)) {
            for (ch in chunk.valid.codePoints()) {
                escapeSingleChar(ch, opt, repr)
            }
            for (byte in chunk.invalid) {
                escapeSingleByte(byte, opt, repr)
            }
        }
    }

    return repr.toString()
}

private fun escapeSingleByte(byte: Byte, opt: EscapeOptions, repr: StringBuilder) {
    val u = byte.toInt() and 0xff
    if (u == 0x00) {
        repr.append("\\0")
    } else if ((u == 0x27 && !opt.escapeSingleQuote) ||
        (u == 0x22 && !opt.escapeDoubleQuote)
    ) {
        repr.append(u.toChar())
    } else {
        // Escapes \t, \r, \n, \\, \', \", and uses \x## for non-ASCII and
        // for ASCII control characters.
        escapeAscii(u, repr)
    }
}

private fun escapeSingleChar(ch: Int, opt: EscapeOptions, repr: StringBuilder) {
    if ((ch == 0x27 && !opt.escapeSingleQuote) ||
        (ch == 0x22 && !opt.escapeDoubleQuote)
    ) {
        repr.appendCodePoint(ch)
    } else {
        // Escapes \0, \t, \r, \n, \\, \', \", and uses \u{...} for
        // non-printable characters and for Grapheme_Extend characters, which
        // includes things like U+0300 "Combining Grave Accent".
        escapeDebug(ch, repr)
    }
}

/**
 * Mirrors Rust's `u8::escape_ascii`: standard ASCII escapes for the
 * named control bytes, printable ASCII passes through, everything else
 * (`<0x20`, `0x7f`, `>=0x80`) becomes `\xHH` with lowercase hex.
 */
private fun escapeAscii(u: Int, repr: StringBuilder) {
    when (u) {
        0x09 -> repr.append("\\t")
        0x0a -> repr.append("\\n")
        0x0d -> repr.append("\\r")
        0x5c -> repr.append("\\\\")
        0x27 -> repr.append("\\'")
        0x22 -> repr.append("\\\"")
        in 0x20..0x7e -> repr.append(u.toChar())
        else -> {
            repr.append("\\x")
            repr.append(u.toString(16).padStart(2, '0'))
        }
    }
}

/**
 * Mirrors Rust's `char::escape_debug`: standard escapes for the named
 * control characters, printable Unicode passes through, and anything
 * else (including combining marks per `Grapheme_Extend`) becomes
 * `\u{...}` with lowercase hex.
 *
 * Kotlin has no direct `Grapheme_Extend` predicate. The closest stdlib
 * approximation walks Unicode general categories: marks (Mn/Mc/Me) and
 * other non-printable categories take the `\u{...}` path, matching the
 * spirit of upstream Rust's printable/extended decision.
 */
private fun escapeDebug(ch: Int, repr: StringBuilder) {
    when (ch) {
        0x00 -> { repr.append("\\0"); return }
        0x09 -> { repr.append("\\t"); return }
        0x0a -> { repr.append("\\n"); return }
        0x0d -> { repr.append("\\r"); return }
        0x5c -> { repr.append("\\\\"); return }
        0x27 -> { repr.append("\\'"); return }
        0x22 -> { repr.append("\\\""); return }
    }
    if (isPrintableUnicode(ch) && !isGraphemeExtend(ch)) {
        repr.appendCodePoint(ch)
    } else {
        repr.append("\\u{")
        repr.append(ch.toString(16))
        repr.append('}')
    }
}

private fun isPrintableUnicode(codePoint: Int): Boolean {
    if (codePoint in 0x20..0x7e) return true
    val type = unicodeCategory(codePoint)
    return when (type) {
        CharCategory.UPPERCASE_LETTER,
        CharCategory.LOWERCASE_LETTER,
        CharCategory.TITLECASE_LETTER,
        CharCategory.MODIFIER_LETTER,
        CharCategory.OTHER_LETTER,
        CharCategory.DECIMAL_DIGIT_NUMBER,
        CharCategory.LETTER_NUMBER,
        CharCategory.OTHER_NUMBER,
        CharCategory.CONNECTOR_PUNCTUATION,
        CharCategory.DASH_PUNCTUATION,
        CharCategory.START_PUNCTUATION,
        CharCategory.END_PUNCTUATION,
        CharCategory.INITIAL_QUOTE_PUNCTUATION,
        CharCategory.FINAL_QUOTE_PUNCTUATION,
        CharCategory.OTHER_PUNCTUATION,
        CharCategory.MATH_SYMBOL,
        CharCategory.CURRENCY_SYMBOL,
        CharCategory.MODIFIER_SYMBOL,
        CharCategory.OTHER_SYMBOL,
        CharCategory.SPACE_SEPARATOR -> true
        else -> false
    }
}

private fun isGraphemeExtend(codePoint: Int): Boolean {
    val type = unicodeCategory(codePoint)
    return type == CharCategory.NON_SPACING_MARK ||
        type == CharCategory.ENCLOSING_MARK
}

private fun unicodeCategory(codePoint: Int): CharCategory? {
    if (codePoint < 0 || codePoint > 0x10ffff) return null
    if (codePoint <= 0xffff) {
        return codePoint.toChar().category
    }
    // Supplementary plane characters: derive from the high surrogate; this
    // is good enough for the printable/mark distinction proc-macro needs,
    // and matches what Char.category exposes for surrogate pairs in KMP.
    val highSurrogate = (((codePoint - 0x10000) shr 10) + 0xd800).toChar()
    return highSurrogate.category
}

private fun StringBuilder.appendCodePoint(codePoint: Int): StringBuilder {
    if (codePoint <= 0xffff) {
        append(codePoint.toChar())
    } else {
        val offset = codePoint - 0x10000
        append((0xd800 + (offset shr 10)).toChar())
        append((0xdc00 + (offset and 0x3ff)).toChar())
    }
    return this
}

private data class Utf8Chunk(val valid: String, val invalid: ByteArray)

private fun String.codePoints(): Sequence<Int> = sequence {
    var i = 0
    while (i < length) {
        val c = this@codePoints[i]
        if (c.isHighSurrogate() && i + 1 < length && this@codePoints[i + 1].isLowSurrogate()) {
            val low = this@codePoints[i + 1]
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
 * Walks `bytes` and yields runs of valid UTF-8 (decoded into a Kotlin
 * `String`) interleaved with the invalid byte sequence that terminated
 * each run, mirroring Rust's `bytes.utf8_chunks()`.
 */
private fun utf8Chunks(bytes: ByteArray): Sequence<Utf8Chunk> = sequence {
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
private fun utf8SequenceLength(bytes: ByteArray, offset: Int): Int {
    val b0 = bytes[offset].toInt() and 0xff
    return when {
        b0 < 0x80 -> 1
        b0 < 0xc2 -> 0
        b0 < 0xe0 -> {
            if (offset + 1 >= bytes.size) 0
            else if (!isUtf8Continuation(bytes[offset + 1])) 0
            else 2
        }
        b0 < 0xf0 -> {
            if (offset + 2 >= bytes.size) return 0
            val b1 = bytes[offset + 1].toInt() and 0xff
            val b2 = bytes[offset + 2].toInt() and 0xff
            val b1Lo = when (b0) {
                0xe0 -> 0xa0
                else -> 0x80
            }
            val b1Hi = when (b0) {
                0xed -> 0x9f
                else -> 0xbf
            }
            if (b1 !in b1Lo..b1Hi) 0
            else if (b2 !in 0x80..0xbf) 0
            else 3
        }
        b0 < 0xf5 -> {
            if (offset + 3 >= bytes.size) return 0
            val b1 = bytes[offset + 1].toInt() and 0xff
            val b2 = bytes[offset + 2].toInt() and 0xff
            val b3 = bytes[offset + 3].toInt() and 0xff
            val b1Lo = when (b0) {
                0xf0 -> 0x90
                else -> 0x80
            }
            val b1Hi = when (b0) {
                0xf4 -> 0x8f
                else -> 0xbf
            }
            if (b1 !in b1Lo..b1Hi) 0
            else if (b2 !in 0x80..0xbf) 0
            else if (b3 !in 0x80..0xbf) 0
            else 4
        }
        else -> 0
    }
}

private fun isUtf8Continuation(byte: Byte): Boolean {
    val u = byte.toInt() and 0xff
    return u in 0x80..0xbf
}
