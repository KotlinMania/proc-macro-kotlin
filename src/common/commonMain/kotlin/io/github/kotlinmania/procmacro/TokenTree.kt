package io.github.kotlinmania.procmacro

import io.github.kotlinmania.procmacro.bridge.cloneTokenTree

/**
 * A single token or a delimited sequence of token trees.
 */
public sealed class TokenTree {
    /**
     * A token stream surrounded by bracket delimiters.
     *
     * Variants are regular classes rather than data classes because two
     * token trees do not participate in structural equality when spans
     * differ.
     */
    public class Group(
        public val value: io.github.kotlinmania.procmacro.Group,
    ) : TokenTree()

    /** An identifier token. */
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

    /** Returns an independent copy of this [TokenTree]. */
    public fun clone(): TokenTree = cloneTokenTree(this)

    /**
     * Returns the span of this tree, delegating to the `span` method of
     * the contained token or delimited stream.
     */
    public fun span(): Span =
        when (this) {
            is Group -> value.span()
            is Ident -> value.span()
            is Punct -> value.span()
            is Literal -> value.span()
        }

    /**
     * Configures the span for only this token.
     *
     * If this token is a [Group], this method does not configure the span
     * of each internal token; it delegates to the `setSpan` method of the
     * variant.
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
     * Prints the token tree as a string convertible back into the same
     * token tree (modulo spans), except for [Group] tokens with
     * [Delimiter.NONE] delimiters and negative numeric literals.
     */
    override fun toString(): String =
        when (this) {
            is Group -> value.toString()
            is Ident -> value.toString()
            is Punct -> value.toString()
            is Literal -> value.toString()
        }
}
