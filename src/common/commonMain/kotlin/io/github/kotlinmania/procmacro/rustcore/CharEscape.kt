package io.github.kotlinmania.procmacro.rustcore

/**
 * Standard ASCII escape formatting for byte literals: named escapes for
 * tab, newline, carriage return, backslash, single quote, and double quote;
 * printable ASCII passes through; all other bytes format as lowercase hex.
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
 * Unicode debug escape formatting for character and string literals: named
 * escapes for NUL, tab, newline, carriage return, backslash, single quote,
 * and double quote; printable non-combining Unicode scalars pass through;
 * all other scalars format as braced lowercase hex escapes.
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
        repr.appendUnicodeScalar(ch)
    } else {
        repr.append("\\u{")
        repr.append(ch.toString(16))
        repr.append('}')
    }
}

internal fun isPrintableUnicode(codePoint: Int): Boolean {
    if (codePoint in 0x20..0x7e) return true
    if (codePoint > 0xffff) {
        return isPrintableSupplementary(codePoint)
    }
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
    if (codePoint > 0xffff) {
        return isSupplementaryGraphemeExtend(codePoint)
    }
    val type = unicodeCategory(codePoint)
    return type == CharCategory.NON_SPACING_MARK ||
        type == CharCategory.ENCLOSING_MARK
}

private fun unicodeCategory(codePoint: Int): CharCategory? {
    if (codePoint !in 0..0xffff) return null
    return codePoint.toChar().category
}

private fun isPrintableSupplementary(codePoint: Int): Boolean {
    if (codePoint !in 0x10000..0x3ffff) return false
    if ((codePoint and 0xfffe) == 0xfffe) return false
    return when (codePoint) {
        0x110bd,
        0x110cd,
        in 0x13430..0x1343f,
        in 0x1bca0..0x1bca3,
        in 0x1d173..0x1d17a,
        -> false
        else -> true
    }
}

private fun isSupplementaryGraphemeExtend(codePoint: Int): Boolean =
    codePoint in 0x1d165..0x1d169 ||
        codePoint in 0x1d16d..0x1d172 ||
        codePoint in 0x1d17b..0x1d182 ||
        codePoint in 0x1d185..0x1d18b ||
        codePoint in 0x1d1aa..0x1d1ad ||
        codePoint in 0xe0100..0xe01ef
