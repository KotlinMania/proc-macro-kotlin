// port-lint: source src/lib.rs
package io.github.kotlinmania.procmacro

/**
 * An identifier (`ident`).
 */
public class Ident internal constructor(
    internal val data: IdentData,
) {
    public companion object {
        /**
         * Creates a new [Ident] with the given `string` as well as the
         * specified `span`. The `string` argument must be a valid
         * identifier permitted by the language (including keywords, e.g.
         * `self` or `fn`). Otherwise, the function will throw.
         *
         * The constructed identifier will be NFC-normalized. See the
         * [Reference](https://doc.rust-lang.org/nightly/reference/identifiers.html#r-ident.normalization)
         * for more info.
         *
         * Note that `span` configures the hygiene information for this
         * identifier. As of this time [Span.callSite] explicitly opts-in
         * to "call-site" hygiene meaning that identifiers created with
         * this span will be resolved as if they were written directly at
         * the location of the macro call, and other code at the macro
         * call site will be able to refer to them as well.
         *
         * Later spans like [Span.defSite] will allow to opt-in to
         * "definition-site" hygiene meaning that identifiers created with
         * this span will be resolved at the location of the macro
         * definition and other code at the macro call site will not be
         * able to refer to them.
         *
         * Due to the current importance of hygiene this constructor,
         * unlike other tokens, requires a [Span] to be specified at
         * construction.
         */
        public fun new(string: String, span: Span): Ident {
            requireValidIdent(string, raw = false)
            return Ident(IdentData(sym = string, isRaw = false, span = span))
        }

        /**
         * Same as [new], but creates a raw identifier (`r#ident`).
         * The `string` argument must be a valid identifier permitted by
         * the language (including keywords, e.g. `fn`). Keywords which
         * are usable in path segments (e.g. `self`, `super`) are not
         * supported, and will cause this constructor to throw.
         */
        public fun newRaw(string: String, span: Span): Ident {
            requireValidIdent(string, raw = true)
            return Ident(IdentData(sym = string, isRaw = true, span = span))
        }

        /**
         * Identifier validation. Phase-1 implements a permissive check
         * adequate for the public API contract (non-empty, starts with a
         * unicode letter or underscore, subsequent characters are letters
         * digits or underscores, raw form rejects `self` / `super` /
         * `Self` / `crate`). Phase 3 will replace this with the
         * [`KotlinLexer`][org.jetbrains.kotlin.kmp.lexer.KotlinLexer]'s
         * `IDENTIFIER` recognizer so the language's exact rules
         * (XID_Start / XID_Continue) are enforced consistently.
         */
        private fun requireValidIdent(string: String, raw: Boolean) {
            require(string.isNotEmpty()) {
                "`Ident` cannot be empty"
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

        private val RAW_IDENT_DISALLOWED: Set<String> = setOf(
            "self", "super", "Self", "crate",
        )
    }

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
     * Prints the identifier as a string that should be losslessly
     * convertible back into the same identifier.
     */
    override fun toString(): String =
        if (data.isRaw) "r#${data.sym}" else data.sym
}

/**
 * Internal backing store for [Ident].
 *
 * Upstream: `bridge::Ident<bridge::client::Span, bridge::client::Symbol>`.
 * The Kotlin port stores the identifier symbol as a plain [String]; phase
 * 3 may introduce a vendored `Symbol` interner if measurement shows it
 * earns its keep.
 */
internal data class IdentData(
    val sym: String,
    val isRaw: Boolean,
    var span: Span,
)
