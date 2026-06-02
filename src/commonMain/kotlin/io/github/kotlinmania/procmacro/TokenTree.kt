// port-lint: source src/lib.rs
package io.github.kotlinmania.procmacro

/**
 * A single token or a delimited sequence of token trees (e.g.,
 * `[1, (), ..]`).
 */
public sealed class TokenTree {
    /**
     * A token stream surrounded by bracket delimiters.
     *
     * Upstream `proc_macro` does not derive `PartialEq` on [TokenTree]
     * variants — two token trees are considered distinct even when the
     * underlying token content matches, because spans differ. The Kotlin
     * port preserves that restraint: these variants are regular classes
     * rather than `data class` so they don't gain structural equality the
     * upstream type doesn't have.
     */
    public class Group(
        public val value: io.github.kotlinmania.procmacro.Group,
    ) : TokenTree()

    /** An identifier. */
    public class Ident(
        public val value: io.github.kotlinmania.procmacro.Ident,
    ) : TokenTree()

    /** A single punctuation character (`+`, `,`, `$`, etc.). */
    public class Punct(
        public val value: io.github.kotlinmania.procmacro.Punct,
    ) : TokenTree()

    /** A literal character (`'a'`), string (`"hello"`), number (`2.3`), etc. */
    public class Literal(
        public val value: io.github.kotlinmania.procmacro.Literal,
    ) : TokenTree()

    /**
     * Returns the span of this tree, delegating to the `span` method of
     * the contained token or a delimited stream.
     */
    public fun span(): Span =
        when (this) {
            is Group -> value.span()
            is Ident -> value.span()
            is Punct -> value.span()
            is Literal -> value.span()
        }

    /**
     * Configures the span for *only this token*.
     *
     * Note that if this token is a [Group] then this method will not
     * configure the span of each of the internal tokens, this will simply
     * delegate to the `setSpan` method of each variant.
     */
    public fun setSpan(span: Span) {
        when (this) {
            is Group -> value.setSpan(span)
            is Ident -> value.setSpan(span)
            is Punct -> value.setSpan(span)
            is Literal -> value.setSpan(span)
        }
    }

    /**
     * Prints the token tree as a string that is supposed to be losslessly
     * convertible back into the same token tree (modulo spans), except
     * for possibly [Group]s with [Delimiter.NONE] delimiters and negative
     * numeric literals.
     *
     * Note: the exact form of the output is subject to change, e.g. there
     * might be changes in the whitespace used between tokens. Therefore,
     * you should *not* do any kind of simple substring matching on the
     * output string (as produced by [toString]) to implement a proc
     * macro, because that matching might stop working if such changes
     * happen. Instead, you should work at the [TokenTree] level, e.g.
     * matching against [TokenTree.Ident], [TokenTree.Punct], or
     * [TokenTree.Literal].
     */
    override fun toString(): String =
        when (this) {
            is Group -> value.toString()
            is Ident -> value.toString()
            is Punct -> value.toString()
            is Literal -> value.toString()
        }
}
