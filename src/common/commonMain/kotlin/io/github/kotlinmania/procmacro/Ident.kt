package io.github.kotlinmania.procmacro

/**
 * An identifier token.
 */
public class Ident internal constructor(
    internal val data: IdentData,
) {
    public companion object {
        /**
         * Creates a new [Ident] with the given [string] and [span].
         *
         * The [string] argument must be a valid identifier permitted by the
         * language (including keywords); otherwise this function throws
         * [IllegalArgumentException].
         */
        public fun new(
            string: String,
            span: Span,
        ): Ident {
            requireValidIdent(string, raw = false)
            return Ident(IdentData(sym = string, isRaw = false, span = span))
        }

        /**
         * Creates a raw identifier (`r#ident`) with the given [string] and
         * [span]. Path-segment keywords and `_` cannot be raw identifiers
         * and cause this constructor to throw [IllegalArgumentException].
         */
        public fun newRaw(
            string: String,
            span: Span,
        ): Ident {
            requireValidIdent(string, raw = true)
            return Ident(IdentData(sym = string, isRaw = true, span = span))
        }

        private fun requireValidIdent(
            string: String,
            raw: Boolean,
        ) {
            require(string.isNotEmpty()) {
                "`Ident` cannot be empty"
            }
            if (string == "\$crate") {
                require(!raw) { "`$string` is not a valid raw identifier" }
                return
            }
            val first = string[0]
            require(first.isLetter() || first == '_') {
                "Invalid identifier: $string"
            }
            for (i in 1 until string.length) {
                val ch = string[i]
                require(ch.isLetterOrDigit() || ch == '_') {
                    "Invalid identifier: $string"
                }
            }
            if (raw) {
                require(string !in RAW_IDENT_DISALLOWED) {
                    "`$string` is not a valid raw identifier"
                }
            }
        }

        private val RAW_IDENT_DISALLOWED: Set<String> =
            setOf(
                "_",
                "self",
                "super",
                "Self",
                "crate",
            )
    }

    /** Returns an independent copy of this [Ident]. */
    public fun clone(): Ident =
        Ident(
            IdentData(
                sym = data.sym,
                isRaw = data.isRaw,
                span = data.span,
            ),
        )

    /**
     * Returns the span of this [Ident], encompassing the entire string
     * returned by [toString].
     */
    public fun span(): Span = data.span

    /**
     * Configures the span of this [Ident], possibly changing its hygiene
     * context.
     */
    public fun setSpan(span: Span) {
        data.span = span
    }

    /**
     * Prints the identifier as a string convertible back into the same
     * identifier.
     */
    override fun toString(): String = if (data.isRaw) "r#${data.sym}" else data.sym
}

/**
 * Internal backing store for [Ident].
 */
internal data class IdentData(
    val sym: String,
    val isRaw: Boolean,
    var span: Span,
)
