// port-lint: source src/lib.rs
package io.github.kotlinmania.procmacro

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
        public fun u8Suffixed(n: UByte): Literal =
            integer(n.toString(), suffix = "u8")
        public fun u16Suffixed(n: UShort): Literal =
            integer(n.toString(), suffix = "u16")
        public fun u32Suffixed(n: UInt): Literal =
            integer(n.toString(), suffix = "u32")
        public fun u64Suffixed(n: ULong): Literal =
            integer(n.toString(), suffix = "u64")
        public fun u128Suffixed(n: ULong): Literal =
            integer(n.toString(), suffix = "u128")
        public fun usizeSuffixed(n: ULong): Literal =
            integer(n.toString(), suffix = "usize")
        public fun i8Suffixed(n: Byte): Literal =
            integer(n.toString(), suffix = "i8")
        public fun i16Suffixed(n: Short): Literal =
            integer(n.toString(), suffix = "i16")
        public fun i32Suffixed(n: Int): Literal =
            integer(n.toString(), suffix = "i32")
        public fun i64Suffixed(n: Long): Literal =
            integer(n.toString(), suffix = "i64")
        public fun i128Suffixed(n: Long): Literal =
            integer(n.toString(), suffix = "i128")
        public fun isizeSuffixed(n: Long): Literal =
            integer(n.toString(), suffix = "isize")

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
            val escape = EscapeOptions(
                escapeSingleQuote = false,
                escapeDoubleQuote = true,
                escapeNonAscii = false,
            )
            val repr = escapeBytes(string.encodeToByteArray(), escape)
            return Literal(literalAt(LitKind.STR, repr, suffix = null))
        }

        /** Character literal. */
        public fun character(ch: Char): Literal {
            val escape = EscapeOptions(
                escapeSingleQuote = true,
                escapeDoubleQuote = false,
                escapeNonAscii = false,
            )
            val repr = escapeBytes(ch.toString().encodeToByteArray(), escape)
            return Literal(literalAt(LitKind.CHAR, repr, suffix = null))
        }

        /** Byte character literal. */
        public fun byteCharacter(byte: Byte): Literal {
            val escape = EscapeOptions(
                escapeSingleQuote = true,
                escapeDoubleQuote = false,
                escapeNonAscii = true,
            )
            val repr = escapeBytes(byteArrayOf(byte), escape)
            return Literal(literalAt(LitKind.BYTE, repr, suffix = null))
        }

        /** Byte string literal. */
        public fun byteString(bytes: ByteArray): Literal {
            val escape = EscapeOptions(
                escapeSingleQuote = false,
                escapeDoubleQuote = true,
                escapeNonAscii = true,
            )
            val repr = escapeBytes(bytes, escape)
            return Literal(literalAt(LitKind.BYTE_STR, repr, suffix = null))
        }

        /** C string literal. */
        public fun cString(string: ByteArray): Literal {
            val escape = EscapeOptions(
                escapeSingleQuote = false,
                escapeDoubleQuote = true,
                escapeNonAscii = false,
            )
            val repr = escapeBytes(string, escape)
            return Literal(literalAt(LitKind.C_STR, repr, suffix = null))
        }

        private fun integer(value: String, suffix: String?): Literal =
            Literal(literalAt(LitKind.INTEGER, value, suffix))

        private fun float(value: String, suffix: String?): Literal =
            Literal(literalAt(LitKind.FLOAT, value, suffix))

        private fun literalAt(kind: LitKind, symbol: String, suffix: String?): LiteralData =
            LiteralData(kind = kind, symbol = symbol, suffix = suffix, span = Span.callSite())

        /**
         * Float literals that don't carry a `.` decimal need one appended
         * so the emitted source token is unambiguous (`1` versus `1.0`).
         * Mirrors upstream `f{32,64}_unsuffixed`.
         */
        private fun canonicalFloat(repr: String): String =
            if ('.' in repr) repr else "$repr.0"
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
     * Prints the literal as a string that should be losslessly
     * convertible back into the same literal (except for possible
     * rounding for floating point literals).
     */
    override fun toString(): String = buildString {
        with(data) {
            when (kind) {
                LitKind.BYTE -> append("b'").append(symbol).append('\'')
                LitKind.CHAR -> append('\'').append(symbol).append('\'')
                LitKind.STR -> append('"').append(symbol).append('"')
                is LitKind.STR_RAW -> {
                    val hashes = "#".repeat(kind.numHashes)
                    append('r').append(hashes).append('"').append(symbol).append('"').append(hashes)
                }
                LitKind.BYTE_STR -> append("b\"").append(symbol).append('"')
                is LitKind.BYTE_STR_RAW -> {
                    val hashes = "#".repeat(kind.numHashes)
                    append("br").append(hashes).append('"').append(symbol).append('"').append(hashes)
                }
                LitKind.C_STR -> append("c\"").append(symbol).append('"')
                is LitKind.C_STR_RAW -> {
                    val hashes = "#".repeat(kind.numHashes)
                    append("cr").append(hashes).append('"').append(symbol).append('"').append(hashes)
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
 * Internal classification of literal kinds. Mirrors upstream
 * `bridge::LitKind`. Raw variants carry the count of hash signs that
 * surrounded the original literal.
 */
internal sealed class LitKind {
    internal data object BYTE : LitKind()
    internal data object CHAR : LitKind()
    internal data object STR : LitKind()
    internal data class STR_RAW(val numHashes: Int) : LitKind()
    internal data object BYTE_STR : LitKind()
    internal data class BYTE_STR_RAW(val numHashes: Int) : LitKind()
    internal data object C_STR : LitKind()
    internal data class C_STR_RAW(val numHashes: Int) : LitKind()
    internal data object INTEGER : LitKind()
    internal data object FLOAT : LitKind()
    internal data object ERR_WITH_GUAR : LitKind()
}
