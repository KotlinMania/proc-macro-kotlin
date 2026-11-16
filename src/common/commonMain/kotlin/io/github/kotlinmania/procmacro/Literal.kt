package io.github.kotlinmania.procmacro

import io.github.kotlinmania.procmacro.bridge.BridgeMethods
import io.github.kotlinmania.procmacro.bridge.ClientSpan
import io.github.kotlinmania.procmacro.bridge.Result
import io.github.kotlinmania.procmacro.bridge.toPublicLitKind
import io.github.kotlinmania.procmacro.rustcore.appendUnicodeScalar
import io.github.kotlinmania.rustcliteralescaper.unescapeByte
import io.github.kotlinmania.rustcliteralescaper.unescapeByteStr
import io.github.kotlinmania.rustcliteralescaper.unescapeCStr
import io.github.kotlinmania.rustcliteralescaper.unescapeChar
import io.github.kotlinmania.rustcliteralescaper.unescapeStr

/**
 * A literal string (`"hello"`), byte string (`b"hello"`), C string
 * (`c"hello"`), character (`'a'`), byte character (`b'a'`), or an integer
 * or floating-point number with or without a suffix (`1`, `1u8`, `2.3`,
 * `2.3f32`). Boolean literals like `true` and `false` do not belong here;
 * they are [Ident] tokens.
 */
public class Literal internal constructor(
    internal val data: LiteralData,
) {
    public companion object {
        /**
         * Creates a new suffixed integer literal with the specified value.
         *
         * Literals created from negative numbers might not survive round-trips
         * through [TokenStream] or strings and may be broken into two tokens
         * (`-` and positive literal).
         *
         * Literals created through this method have the [Span.callSite] span
         * by default, which can be configured with [setSpan].
         */
        public fun u8Suffixed(n: UByte): Literal = integer(n.toString(), suffix = "u8")

        public fun u16Suffixed(n: UShort): Literal = integer(n.toString(), suffix = "u16")

        public fun u32Suffixed(n: UInt): Literal = integer(n.toString(), suffix = "u32")

        public fun u64Suffixed(n: ULong): Literal = integer(n.toString(), suffix = "u64")

        public fun u128Suffixed(n: ULong): Literal = integer(n.toString(), suffix = "u128")

        public fun usizeSuffixed(n: ULong): Literal = integer(n.toString(), suffix = "usize")

        public fun i8Suffixed(n: Byte): Literal = integer(n.toString(), suffix = "i8")

        public fun i16Suffixed(n: Short): Literal = integer(n.toString(), suffix = "i16")

        public fun i32Suffixed(n: Int): Literal = integer(n.toString(), suffix = "i32")

        public fun i64Suffixed(n: Long): Literal = integer(n.toString(), suffix = "i64")

        public fun i128Suffixed(n: Long): Literal = integer(n.toString(), suffix = "i128")

        public fun isizeSuffixed(n: Long): Literal = integer(n.toString(), suffix = "isize")

        /**
         * Creates a new unsuffixed integer literal with the specified value.
         *
         * No suffix is specified on this token, meaning that invocations like
         * [i8Unsuffixed] produce the same token text as [u32Unsuffixed] for
         * equal numeric values.
         */
        public fun u8Unsuffixed(n: UByte): Literal = integer(n.toString(), suffix = null)

        public fun u16Unsuffixed(n: UShort): Literal = integer(n.toString(), suffix = null)

        public fun u32Unsuffixed(n: UInt): Literal = integer(n.toString(), suffix = null)

        public fun u64Unsuffixed(n: ULong): Literal = integer(n.toString(), suffix = null)

        public fun u128Unsuffixed(n: ULong): Literal = integer(n.toString(), suffix = null)

        public fun usizeUnsuffixed(n: ULong): Literal = integer(n.toString(), suffix = null)

        public fun i8Unsuffixed(n: Byte): Literal = integer(n.toString(), suffix = null)

        public fun i16Unsuffixed(n: Short): Literal = integer(n.toString(), suffix = null)

        public fun i32Unsuffixed(n: Int): Literal = integer(n.toString(), suffix = null)

        public fun i64Unsuffixed(n: Long): Literal = integer(n.toString(), suffix = null)

        public fun i128Unsuffixed(n: Long): Literal = integer(n.toString(), suffix = null)

        public fun isizeUnsuffixed(n: Long): Literal = integer(n.toString(), suffix = null)

        /**
         * Creates a new unsuffixed floating-point literal.
         *
         * Throws if the specified float is not finite (infinity or NaN).
         */
        public fun f32Unsuffixed(n: Float): Literal {
            require(n.isFinite()) { "Invalid float literal $n" }
            return float(formatFloat(n), suffix = null)
        }

        /**
         * Creates a new suffixed floating-point literal.
         *
         * Throws if the specified float is not finite (infinity or NaN).
         */
        public fun f32Suffixed(n: Float): Literal {
            require(n.isFinite()) { "Invalid float literal $n" }
            return float(formatFloat(n), suffix = "f32")
        }

        /**
         * Creates a new unsuffixed floating-point literal at 64-bit precision.
         *
         * Throws if the specified float is not finite (infinity or NaN).
         */
        public fun f64Unsuffixed(n: Double): Literal {
            require(n.isFinite()) { "Invalid float literal $n" }
            return float(formatDouble(n), suffix = null)
        }

        /**
         * Creates a new suffixed floating-point literal at 64-bit precision.
         *
         * Throws if the specified float is not finite (infinity or NaN).
         */
        public fun f64Suffixed(n: Double): Literal {
            require(n.isFinite()) { "Invalid float literal $n" }
            return float(formatDouble(n), suffix = "f64")
        }

        /** String literal. */
        public fun string(string: String): Literal {
            val escape =
                EscapeOptions(
                    escapeSingleQuote = false,
                    escapeDoubleQuote = true,
                    escapeNonAscii = false,
                )
            val repr = escapeBytes(string.encodeToByteArray(), escape)
            return Literal(literalAt(LitKind.STR, repr, suffix = null))
        }

        /** Character literal for a UTF-16 BMP character. */
        public fun character(ch: Char): Literal {
            require(!ch.isSurrogate()) { "Surrogate code unit is not a valid Unicode scalar: ${ch.code}" }
            return characterCodePoint(ch.code)
        }

        /** Character literal for any 21-bit Unicode scalar value. */
        public fun characterCodePoint(codePoint: Int): Literal {
            require(codePoint in 0..0x10FFFF && codePoint !in 0xD800..0xDFFF) {
                "Invalid Unicode scalar value: $codePoint"
            }
            val escape =
                EscapeOptions(
                    escapeSingleQuote = true,
                    escapeDoubleQuote = false,
                    escapeNonAscii = false,
                )
            val text = buildString { appendUnicodeScalar(codePoint) }
            val repr = escapeBytes(text.encodeToByteArray(), escape)
            return Literal(literalAt(LitKind.CHAR, repr, suffix = null))
        }

        /** Byte character literal. */
        public fun byteCharacter(byte: Byte): Literal {
            val escape =
                EscapeOptions(
                    escapeSingleQuote = true,
                    escapeDoubleQuote = false,
                    escapeNonAscii = true,
                )
            val repr = escapeBytes(byteArrayOf(byte), escape)
            return Literal(literalAt(LitKind.BYTE, repr, suffix = null))
        }

        /** Byte string literal. */
        public fun byteString(bytes: ByteArray): Literal {
            val escape =
                EscapeOptions(
                    escapeSingleQuote = false,
                    escapeDoubleQuote = true,
                    escapeNonAscii = true,
                )
            val repr = escapeBytes(bytes, escape)
            return Literal(literalAt(LitKind.BYTE_STR, repr, suffix = null))
        }

        /** C string literal. */
        public fun cString(string: ByteArray): Literal {
            require(0.toByte() !in string) { "C string literal bytes must not contain interior NUL bytes" }
            val escape =
                EscapeOptions(
                    escapeSingleQuote = false,
                    escapeDoubleQuote = true,
                    escapeNonAscii = false,
                )
            val repr = escapeBytes(string, escape)
            return Literal(literalAt(LitKind.C_STR, repr, suffix = null))
        }

        /**
         * Parses a single literal from its stringified representation.
         *
         * The input string must contain only the literal token, without
         * surrounding whitespace or comments. The resulting literal has a
         * [Span.callSite] span.
         */
        public fun fromString(src: String): LiteralParseOutcome {
            if (src.isEmpty() || src.first().isWhitespace() || src.last().isWhitespace()) {
                return LiteralParseOutcome.Err(LexError("expected single literal token without whitespace"))
            }
            return when (val parsed = BridgeMethods.literalFromStr(src)) {
                is Result.Ok ->
                    LiteralParseOutcome.Ok(
                        Literal(
                            LiteralData(
                                kind = parsed.value.kind.toPublicLitKind(),
                                symbol = parsed.value.symbol.asString(),
                                suffix = parsed.value.suffix?.asString(),
                                span = parsed.value.span.span,
                            ),
                        ),
                    )
                is Result.Err -> LiteralParseOutcome.Err(LexError(parsed.message))
            }
        }

        /**
         * Parses a single literal from its stringified representation, throwing
         * [IllegalArgumentException] if lexing fails.
         */
        public fun fromStrUnchecked(src: String): Literal =
            when (val outcome = fromString(src)) {
                is LiteralParseOutcome.Ok -> outcome.value
                is LiteralParseOutcome.Err ->
                    throw IllegalArgumentException("Invalid literal '$src': ${outcome.error}")
            }

        private fun integer(
            value: String,
            suffix: String?,
        ): Literal = Literal(literalAt(LitKind.INTEGER, value, suffix))

        private fun float(
            value: String,
            suffix: String?,
        ): Literal = Literal(literalAt(LitKind.FLOAT, value, suffix))

        private fun literalAt(
            kind: LitKind,
            symbol: String,
            suffix: String?,
        ): LiteralData = LiteralData(kind = kind, symbol = symbol, suffix = suffix, span = Span.callSite())

        /**
         * Float literals that do not carry a decimal point need `.0` appended
         * so the emitted source token is unambiguous (`1` versus `1.0`).
         */
        private fun formatFloat(n: Float): String =
            if (n == 0.0f && 1.0 / n.toDouble() == Double.NEGATIVE_INFINITY) {
                "-0.0"
            } else {
                canonicalFloat(n.toString())
            }

        private fun formatDouble(n: Double): String =
            if (n == 0.0 && 1.0 / n == Double.NEGATIVE_INFINITY) {
                "-0.0"
            } else {
                canonicalFloat(n.toString())
            }

        private fun canonicalFloat(repr: String): String =
            if ('.' in repr || 'e' in repr || 'E' in repr) repr else "$repr.0"

        /**
         * Constructs a [Literal] from a Kotlin string source representation
         * (including surrounding quotes). Handles both regular strings
         * and raw strings.
         */
        internal fun fromKotlinString(src: String): Literal =
            when {
                src.startsWith("\"\"\"") && src.endsWith("\"\"\"") -> {
                    val content = src.substring(3, src.length - 3)
                    Literal(literalAt(LitKind.STR_RAW(0), content, suffix = null))
                }
                src.startsWith("\"") && src.endsWith("\"") -> {
                    val content = src.substring(1, src.length - 1)
                    Literal(literalAt(LitKind.STR, content, suffix = null))
                }
                else -> Literal(literalAt(LitKind.STR, src, suffix = null))
            }

        /**
         * Constructs a [Literal] from a Kotlin character source representation
         * (including surrounding single quotes).
         */
        internal fun fromKotlinChar(src: String): Literal {
            val content =
                if (src.startsWith("'") && src.endsWith("'") && src.length >= 2) {
                    src.substring(1, src.length - 1)
                } else {
                    src
                }
            return Literal(literalAt(LitKind.CHAR, content, suffix = null))
        }

        /**
         * Constructs a [Literal] from a Kotlin integer source representation,
         * splitting any Kotlin type suffix (`L`, `u`, `UL`).
         */
        internal fun fromKotlinInteger(src: String): Literal {
            val (digits, suffix) = stripKotlinNumericSuffix(src)
            return Literal(literalAt(LitKind.INTEGER, digits, suffix))
        }

        /**
         * Constructs a [Literal] from a Kotlin floating-point source
         * representation, splitting any Kotlin type suffix (`f`, `F`).
         */
        internal fun fromKotlinFloat(src: String): Literal {
            val (digits, suffix) = stripKotlinNumericSuffix(src)
            return Literal(literalAt(LitKind.FLOAT, digits, suffix))
        }

        private fun stripKotlinNumericSuffix(src: String): Pair<String, String?> {
            val lower = src.lowercase()
            val suffixMatch = kotlinNumericSuffix.find(lower)
            if (suffixMatch != null) {
                val suffixStart = suffixMatch.range.first
                if (suffixStart > 0) {
                    return src.substring(0, suffixStart) to src.substring(suffixStart)
                }
            }
            return src to null
        }

        private val kotlinNumericSuffix = Regex("""[luf]+$""")
    }

    /** Returns an independent copy of this [Literal]. */
    public fun clone(): Literal =
        Literal(
            LiteralData(
                kind = data.kind,
                symbol = data.symbol,
                suffix = data.suffix,
                span = data.span,
            ),
        )

    /** Returns the span encompassing this literal. */
    public fun span(): Span = data.span

    /** Configures the span associated for this literal. */
    public fun setSpan(span: Span) {
        data.span = span
    }

    /**
     * Returns a [Span] that is a subset of [span] containing only the
     * source bytes in [range]. Returns `null` if the requested range is
     * outside the bounds of this literal or if this literal has no
     * byte-backed source span.
     */
    public fun subspan(range: IntRange): Span? =
        BridgeMethods.spanSubspan(ClientSpan(data.span), start = range.first, end = range.last + 1)?.span

    /**
     * Returns the unescaped byte value if this is a byte character literal.
     */
    public fun byteCharacterValue(): ByteCharacterValueOutcome {
        if (data.kind != LitKind.BYTE) {
            return ByteCharacterValueOutcome.Err(ConversionErrorKind.InvalidLiteralKind)
        }
        val outcome = unescapeByte(data.symbol)
        return if (outcome.isOk) {
            ByteCharacterValueOutcome.Ok(outcome.unwrap())
        } else {
            ByteCharacterValueOutcome.Err(ConversionErrorKind.FailedToUnescape(outcome.unwrapErr()))
        }
    }

    /**
     * Returns the unescaped character value if this is a character literal.
     */
    public fun characterValue(): CharacterValueOutcome {
        if (data.kind != LitKind.CHAR) {
            return CharacterValueOutcome.Err(ConversionErrorKind.InvalidLiteralKind)
        }
        val outcome = unescapeChar(data.symbol)
        return if (outcome.isOk) {
            CharacterValueOutcome.Ok(outcome.unwrapCodePoint())
        } else {
            CharacterValueOutcome.Err(ConversionErrorKind.FailedToUnescape(outcome.unwrapErr()))
        }
    }

    /**
     * Returns the unescaped string value if this is a regular or raw string literal.
     */
    public fun strValue(): StringValueOutcome {
        val kind = data.kind
        return when {
            kind == LitKind.STR -> {
                if ('\\' !in data.symbol && '\r' !in data.symbol) {
                    StringValueOutcome.Ok(data.symbol)
                } else {
                    val buf = StringBuilder(data.symbol.length)
                    var fatalError: ConversionErrorKind? = null
                    unescapeStr(data.symbol) { _, res ->
                        if (res.isOk) {
                            buf.appendUnicodeScalar(res.unwrapCodePoint())
                        } else {
                            val err = res.unwrapErr()
                            if (err.isFatal()) {
                                fatalError = ConversionErrorKind.FailedToUnescape(err)
                            }
                        }
                    }
                    val error = fatalError
                    if (error != null) {
                        StringValueOutcome.Err(error)
                    } else {
                        StringValueOutcome.Ok(buf.toString())
                    }
                }
            }
            kind is LitKind.STR_RAW -> StringValueOutcome.Ok(data.symbol)
            else -> StringValueOutcome.Err(ConversionErrorKind.InvalidLiteralKind)
        }
    }

    /**
     * Returns the unescaped C string bytes (including the terminating NUL byte)
     * if this is a regular or raw C string literal.
     */
    public fun cstrValue(): ByteArrayValueOutcome {
        val kind = data.kind
        return when {
            kind == LitKind.C_STR -> {
                val buf = ArrayList<Byte>(data.symbol.length + 1)
                var fatalError: ConversionErrorKind? = null
                unescapeCStr(data.symbol) { _, res ->
                    if (res.isOk) {
                        for (byte in res.unwrap().toUtf8Bytes()) {
                            buf.add(byte)
                        }
                    } else {
                        val err = res.unwrapErr()
                        if (err.isFatal()) {
                            fatalError = ConversionErrorKind.FailedToUnescape(err)
                        }
                    }
                }
                val error = fatalError
                if (error != null) {
                    ByteArrayValueOutcome.Err(error)
                } else {
                    buf.add(0)
                    ByteArrayValueOutcome.Ok(buf.toByteArray())
                }
            }
            kind is LitKind.C_STR_RAW -> ByteArrayValueOutcome.Ok(data.symbol.encodeToByteArray() + 0)
            else -> ByteArrayValueOutcome.Err(ConversionErrorKind.InvalidLiteralKind)
        }
    }

    /**
     * Returns the unescaped byte string bytes if this is a regular or raw
     * byte string literal.
     */
    public fun byteStrValue(): ByteArrayValueOutcome {
        val kind = data.kind
        return when {
            kind == LitKind.BYTE_STR -> {
                val buf = ArrayList<Byte>(data.symbol.length)
                var fatalError: ConversionErrorKind? = null
                unescapeByteStr(data.symbol) { _, res ->
                    if (res.isOk) {
                        buf.add(res.unwrap().toByte())
                    } else {
                        val err = res.unwrapErr()
                        if (err.isFatal()) {
                            fatalError = ConversionErrorKind.FailedToUnescape(err)
                        }
                    }
                }
                val error = fatalError
                if (error != null) {
                    ByteArrayValueOutcome.Err(error)
                } else {
                    ByteArrayValueOutcome.Ok(buf.toByteArray())
                }
            }
            kind is LitKind.BYTE_STR_RAW -> ByteArrayValueOutcome.Ok(data.symbol.encodeToByteArray())
            else -> ByteArrayValueOutcome.Err(ConversionErrorKind.InvalidLiteralKind)
        }
    }

    /**
     * Prints the literal as a string that should be losslessly convertible
     * back into the same literal (except for possible rounding for
     * floating-point literals).
     */
    override fun toString(): String =
        buildString {
            with(data) {
                when (kind) {
                    LitKind.BYTE -> append("b'").append(symbol).append('\'')
                    LitKind.CHAR -> append('\'').append(symbol).append('\'')
                    LitKind.STR -> append('"').append(symbol).append('"')
                    is LitKind.STR_RAW -> {
                        val hashes = "#".repeat(kind.numHashes)
                        append('r')
                            .append(hashes)
                            .append('"')
                            .append(symbol)
                            .append('"')
                            .append(hashes)
                    }
                    LitKind.BYTE_STR -> append("b\"").append(symbol).append('"')
                    is LitKind.BYTE_STR_RAW -> {
                        val hashes = "#".repeat(kind.numHashes)
                        append("br")
                            .append(hashes)
                            .append('"')
                            .append(symbol)
                            .append('"')
                            .append(hashes)
                    }
                    LitKind.C_STR -> append("c\"").append(symbol).append('"')
                    is LitKind.C_STR_RAW -> {
                        val hashes = "#".repeat(kind.numHashes)
                        append("cr")
                            .append(hashes)
                            .append('"')
                            .append(symbol)
                            .append('"')
                            .append(hashes)
                    }
                    LitKind.INTEGER, LitKind.FLOAT, LitKind.ERR_WITH_GUAR -> append(symbol)
                }
                if (suffix != null) append(suffix)
            }
        }
}

/**
 * Internal backing store for [Literal].
 */
internal data class LiteralData(
    val kind: LitKind,
    val symbol: String,
    val suffix: String?,
    var span: Span,
)

/**
 * Classification of literal kinds. Raw variants carry the count of hash
 * signs that surrounded the original literal.
 */
sealed class LitKind {
    data object BYTE : LitKind()

    data object CHAR : LitKind()

    data object STR : LitKind()

    data class STR_RAW(
        val numHashes: Int,
    ) : LitKind()

    data object BYTE_STR : LitKind()

    data class BYTE_STR_RAW(
        val numHashes: Int,
    ) : LitKind()

    data object C_STR : LitKind()

    data class C_STR_RAW(
        val numHashes: Int,
    ) : LitKind()

    data object INTEGER : LitKind()

    data object FLOAT : LitKind()

    data object ERR_WITH_GUAR : LitKind()
}
