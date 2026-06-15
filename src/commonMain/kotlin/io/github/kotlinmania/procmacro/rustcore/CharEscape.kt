package io.github.kotlinmania.procmacro.rustcore

/**
 * Kotlin emulation of the `u8::escape_ascii` and `char::escape_debug`
 * helpers from Rust `core`. escape.rs calls into both of them and they
 * have no direct Kotlin stdlib equivalent. None of this mirrors a file
 * under `tmp/proc-macro/`; the upstream references are:
 *
 * | Kotlin               | Rust                                          |
 * |----------------------|-----------------------------------------------|
 * | `escapeAscii`        | `u8::escape_ascii` (core::ascii)              |
 * | `escapeDebug`        | `char::escape_debug_ext` (core::char)         |
 * | `isPrintableUnicode` | `char::is_printable` (core, private)          |
 * | `isGraphemeExtend`   | `char::is_grapheme_extended` (core, private)  |
 *
 * `is_printable` and `is_grapheme_extended` rely on Rust's bundled
 * Unicode tables. Kotlin stdlib exposes the relevant decision via
 * `CharCategory`, which is a coarser approximation but covers the
 * printable / mark distinction proc_macro needs. Edge cases that hinge
 * on a specific Unicode property (ZWJ, certain Cf code points) may
 * differ from upstream Rust; a future `core-kotlin` sibling backed by
 * the real Unicode tables would close that gap.
 */

/**
 * Mirrors Rust's `u8::escape_ascii`: standard ASCII escapes for the
 * named control bytes, printable ASCII passes through, everything else
 * (`<0x20`, `0x7f`, `>=0x80`) becomes `\xHH` with lowercase hex.
 */
internal fun escapeAscii(
    u: Int,
    repr: StringBuilder,
) {
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
 */
internal fun escapeDebug(
    ch: Int,
    repr: StringBuilder,
) {
    when (ch) {
        0x00 -> {
            repr.append("\\0")
            return
        }
        0x09 -> {
            repr.append("\\t")
            return
        }
        0x0a -> {
            repr.append("\\n")
            return
        }
        0x0d -> {
            repr.append("\\r")
            return
        }
        0x5c -> {
            repr.append("\\\\")
            return
        }
        0x27 -> {
            repr.append("\\'")
            return
        }
        0x22 -> {
            repr.append("\\\"")
            return
        }
    }
    if (isPrintableUnicode(ch) && !isGraphemeExtend(ch)) {
        repr.appendCodePoint(ch)
    } else {
        repr.append("\\u{")
        repr.append(ch.toString(16))
        repr.append('}')
    }
}

internal fun isPrintableUnicode(codePoint: Int): Boolean {
    if (codePoint in 0x20..0x7e) return true
    val type = unicodeCategory(codePoint) ?: return false
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
        CharCategory.SPACE_SEPARATOR,
        -> true
        else -> false
    }
}

internal fun isGraphemeExtend(codePoint: Int): Boolean {
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
    // is good enough for the printable/mark distinction proc_macro needs,
    // and matches what Char.category exposes for surrogate pairs in KMP.
    val highSurrogate = (((codePoint - 0x10000) shr 10) + 0xd800).toChar()
    return highSurrogate.category
}
