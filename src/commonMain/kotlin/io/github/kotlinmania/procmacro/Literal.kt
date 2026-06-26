// port-lint: source src/lib.rs
package io.github.kotlinmania.procmacro

import io.github.kotlinmania.procmacro.rustcore.unescapeBytes
import io.github.kotlinmania.procmacro.rustcore.unescapeChars

/**
 * A literal string (`"hello"`), byte string (`b"hello"`), C string
 * (`c"hello"`), character (`'a'`), byte character (`b'a'`), an integer or
 * floating point number with or without a suffix (`1`, `1u8`, `2.3`,
 * `2.3f32`). Boolean literals like `true` and `false` do not belong here,
 * they are [Ident]s.
 */
public class Literal internal constructor(
    internal val data: LiteralData,
) {
    public companion object {
        // ----- suffixed integer factories -----

        /**
         * Creates a new suffixed integer literal with the specified value.
         *
         * This function will create an integer like `1u8` where the
         * integer value specified is the first part of the token and the
         * integral type is also suffixed at the end. Literals created
         * from negative numbers might not survive round-trips through
         * [TokenStream] or strings and may be broken into two tokens
         * (`-` and positive literal).
         *
         * Literals created through this method have the [Span.callSite]
         * span by default, which can be configured with the [setSpan]
         * method below.
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

        // ----- unsuffixed integer factories -----

        /**
         * Creates a new unsuffixed integer literal with the specified
         * value.
         *
         * This function will create an integer like `1` where the
         * integer value specified is the first part of the token. No
         * suffix is specified on this token, meaning that invocations
         * like [i8Unsuffixed] are equivalent to [u32Unsuffixed].
         * Literals created from negative numbers might not survive
         * roundtrips through [TokenStream] or strings and may be broken
         * into two tokens (`-` and positive literal).
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

        // ----- floating-point factories -----

        /**
         * Creates a new unsuffixed floating-point literal.
         *
         * This constructor is similar to those like [i8Unsuffixed] where
         * the float's value is emitted directly into the token but no
         * suffix is used, so it may be inferred to be a 32-bit float
         * later in the compiler.
         *
         * Throws if the specified float is not finite (infinity or NaN).
         */
        public fun f32Unsuffixed(n: Float): Literal {
            require(n.isFinite()) { "Invalid float literal $n" }
            return float(canonicalFloat(n.toString()), suffix = null)
        }

        /**
         * Creates a new suffixed floating-point literal.
         *
         * This constructor will create a literal like `1.0f32` where the
         * value specified is the preceding part of the token and `f32` is
         * the suffix of the token.
         *
         * Throws if the specified float is not finite (infinity or NaN).
         */
        public fun f32Suffixed(n: Float): Literal {
            require(n.isFinite()) { "Invalid float literal $n" }
            return float(n.toString(), suffix = "f32")
        }

        /**
         * Creates a new unsuffixed floating-point literal.
         *
         * Same shape as [f32Unsuffixed] but at 64-bit precision.
         */
        public fun f64Unsuffixed(n: Double): Literal {
            require(n.isFinite()) { "Invalid float literal $n" }
            return float(canonicalFloat(n.toString()), suffix = null)
        }

        /**
         * Creates a new suffixed floating-point literal.
         *
         * Same shape as [f32Suffixed] but at 64-bit precision.
         */
        public fun f64Suffixed(n: Double): Literal {
            require(n.isFinite()) { "Invalid float literal $n" }
            return float(n.toString(), suffix = "f64")
        }

        // ----- string / char / byte factories -----

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

        /** Character literal. */
        public fun character(ch: Char): Literal {
            val escape =
                EscapeOptions(
                    escapeSingleQuote = true,
                    escapeDoubleQuote = false,
                    escapeNonAscii = false,
                )
            val repr = escapeBytes(ch.toString().encodeToByteArray(), escape)
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
            val escape =
                EscapeOptions(
                    escapeSingleQuote = false,
                    escapeDoubleQuote = true,
                    escapeNonAscii = false,
                )
            val repr = escapeBytes(string, escape)
            return Literal(literalAt(LitKind.C_STR, repr, suffix = null))
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
         * Float literals that don't carry a `.` decimal need one appended
         * so the emitted source token is unambiguous (`1` versus `1.0`).
         * Mirrors upstream `f{32,64}_unsuffixed`.
         */
        private fun canonicalFloat(repr: String): String = if ('.' in repr) repr else "$repr.0"

        // --- Kotlin-source factory methods (Compiler variant) ---

        /**
         * Constructs a [Literal] from a Kotlin string source representation
         * (including surrounding quotes). Handles both regular strings
         * and raw strings.
         */
        internal fun fromKotlinString(src: String): Literal =
            when {
                src.startsWith("\"\"\"") && src.endsWith("\"\"\"") -> {
                    val content = src.substring(3, src.length - 3)
                    Literal(literalAt(LitKind.STR, content, suffix = null))
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
         * stripping any Kotlin type suffix (L, u, UL, etc.).
         */
        internal fun fromKotlinInteger(src: String): Literal {
            val (digits, suffix) = stripKotlinNumericSuffix(src)
            return Literal(literalAt(LitKind.INTEGER, digits, suffix))
        }

        /**
         * Constructs a [Literal] from a Kotlin floating-point source
         * representation, stripping any Kotlin type suffix (f, F).
         */
        internal fun fromKotlinFloat(src: String): Literal {
            val (digits, suffix) = stripKotlinNumericSuffix(src)
            return Literal(literalAt(LitKind.FLOAT, digits, suffix))
        }

        /**
         * Splits a Kotlin numeric literal into its digit portion and optional
         * suffix. Kotlin integer suffixes: L, u, UL, uL. Kotlin float
         * suffixes: f, F. Underscore separators within the digits are
         * preserved as-is in the symbol.
         */
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

    // ----- instance methods -----

    /** Returns the span encompassing this literal. */
    public fun span(): Span = data.span

    /** Configures the span associated for this literal. */
    public fun setSpan(span: Span) {
        data.span = span
    }

    /**
     * Returns a [Span] that is a subset of [span] containing only the
     * source bytes in `range`. Returns `null` if the would-be trimmed
     * span is outside the bounds of this literal.
     *
     * Phase-1 stub returns `null`. Phase 3 wires this to KotlinLexer
     * source offsets.
     */
    public fun subspan(range: IntRange): Span? = null

    /**
     * Returns the unescaped character value if this is a byte character
     * literal. Mirrors upstream `Literal::byte_character_value`.
     */
    public fun byteCharacterValue(): ByteCharacterValueOutcome {
        if (data.kind != LitKind.BYTE) {
            return ByteCharacterValueOutcome.Err(ConversionErrorKind.InvalidLiteralKind)
        }
        val bytes =
            unescapeBytes(data.symbol)
                ?: return ByteCharacterValueOutcome.Err(
                    ConversionErrorKind.FailedToUnescape(EscapeError.Fatal("invalid escape in byte literal")),
                )
        if (bytes.size != 1) {
            return ByteCharacterValueOutcome.Err(
                ConversionErrorKind.FailedToUnescape(
                    EscapeError.Fatal("byte literal must contain exactly one byte"),
                ),
            )
        }
        return ByteCharacterValueOutcome.Ok(bytes[0].toUByte())
    }

    /**
     * Returns the unescaped character value if this is a character
     * literal. Mirrors upstream `Literal::character_value`.
     */
    public fun characterValue(): CharacterValueOutcome {
        if (data.kind != LitKind.CHAR) {
            return CharacterValueOutcome.Err(ConversionErrorKind.InvalidLiteralKind)
        }
        val chars =
            unescapeChars(data.symbol)
                ?: return CharacterValueOutcome.Err(
                    ConversionErrorKind.FailedToUnescape(EscapeError.Fatal("invalid escape in char literal")),
                )
        if (chars.length != 1) {
            return CharacterValueOutcome.Err(
                ConversionErrorKind.FailedToUnescape(
                    EscapeError.Fatal("char literal must contain exactly one character"),
                ),
            )
        }
        return CharacterValueOutcome.Ok(chars[0])
    }

    /**
     * Returns the unescaped string value if this is a string literal
     * (regular or raw). Mirrors upstream `Literal::str_value`.
     */
    public fun strValue(): StringValueOutcome {
        val kind = data.kind
        return when {
            kind == LitKind.STR -> {
                if ('\\' !in data.symbol) {
                    StringValueOutcome.Ok(data.symbol)
                } else {
                    val out = unescapeChars(data.symbol)
                    if (out == null) {
                        StringValueOutcome.Err(
                            ConversionErrorKind.FailedToUnescape(
                                EscapeError.Fatal("invalid escape in string literal"),
                            ),
                        )
                    } else {
                        StringValueOutcome.Ok(out)
                    }
                }
            }
            kind is LitKind.STR_RAW -> StringValueOutcome.Ok(data.symbol)
            else -> StringValueOutcome.Err(ConversionErrorKind.InvalidLiteralKind)
        }
    }

    /**
     * Returns the unescaped C string bytes (with the terminating NUL)
     * if this is a C string literal (regular or raw). Mirrors upstream
     * `Literal::cstr_value`.
     */
    public fun cstrValue(): ByteArrayValueOutcome {
        val kind = data.kind
        return when {
            kind == LitKind.C_STR -> {
                val bytes = unescapeBytes(data.symbol)
                if (bytes == null) {
                    ByteArrayValueOutcome.Err(
                        ConversionErrorKind.FailedToUnescape(
                            EscapeError.Fatal("invalid escape in c-string literal"),
                        ),
                    )
                } else {
                    ByteArrayValueOutcome.Ok(bytes + 0)
                }
            }
            kind is LitKind.C_STR_RAW -> ByteArrayValueOutcome.Ok(data.symbol.encodeToByteArray() + 0)
            else -> ByteArrayValueOutcome.Err(ConversionErrorKind.InvalidLiteralKind)
        }
    }

    /**
     * Returns the unescaped byte string bytes if this is a byte string
     * literal (regular or raw). Mirrors upstream `Literal::byte_str_value`.
     */
    public fun byteStrValue(): ByteArrayValueOutcome {
        val kind = data.kind
        return when {
            kind == LitKind.BYTE_STR -> {
                val bytes = unescapeBytes(data.symbol)
                if (bytes == null) {
                    ByteArrayValueOutcome.Err(
                        ConversionErrorKind.FailedToUnescape(
                            EscapeError.Fatal("invalid escape in byte-string literal"),
                        ),
                    )
                } else {
                    ByteArrayValueOutcome.Ok(bytes)
                }
            }
            kind is LitKind.BYTE_STR_RAW -> ByteArrayValueOutcome.Ok(data.symbol.encodeToByteArray())
            else -> ByteArrayValueOutcome.Err(ConversionErrorKind.InvalidLiteralKind)
        }
    }

    /**
     * Prints the literal as a string that should be losslessly
     * convertible back into the same literal (except for possible
     * rounding for floating point literals).
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
 *
 * Upstream: `bridge::Literal<bridge::client::Span, bridge::client::Symbol>`.
 */
internal data class LiteralData(
    val kind: LitKind,
    val symbol: String,
    val suffix: String?,
    var span: Span,
)

/**
 * Classification of literal kinds. Mirrors upstream
 * `bridge::LitKind`. Raw variants carry the count of hash signs that
 * surrounded the original literal.
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
