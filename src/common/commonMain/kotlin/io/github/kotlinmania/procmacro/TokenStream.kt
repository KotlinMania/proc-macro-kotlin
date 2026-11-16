package io.github.kotlinmania.procmacro

import io.github.kotlinmania.procmacro.bridge.BridgeMethods
import io.github.kotlinmania.procmacro.bridge.ClientTokenStream
import io.github.kotlinmania.procmacro.bridge.Result
import io.github.kotlinmania.procmacro.tokenstream.IntoIter
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.lexer.KotlinLexer

/**
 * The main type provided by this library, representing an abstract stream
 * of tokens, or more specifically a sequence of token trees. The type
 * provides interfaces for iterating over those token trees and collecting
 * a number of token trees into one stream.
 *
 * This is both the input and output of procedural macro definitions.
 */
public class TokenStream internal constructor(
    internal val data: TokenStreamData,
) : Iterable<TokenTree> {
    public companion object {
        /** Returns an empty [TokenStream] containing no token trees. */
        public fun new(): TokenStream = TokenStream(TokenStreamData(emptyList()))

        /**
         * Creates a token stream containing a single token tree.
         */
        public fun fromTokenTree(tree: TokenTree): TokenStream = TokenStream(TokenStreamData(listOf(tree)))

        /**
         * Attempts to break the string into tokens and parse those tokens
         * into a token stream. May fail if the string contains unbalanced
         * delimiters or unrecognized characters. All tokens in the parsed
         * stream receive [Span.callSite] spans.
         */
        public fun fromString(src: String): TokenStreamParseOutcome {
            val lexer = KotlinLexer()
            return KtTokenAdapter.tokenize(lexer, src)
        }

        /**
         * Collects a sequence of token trees into a single stream.
         */
        public fun fromTokenTrees(trees: Iterable<TokenTree>): TokenStream =
            TokenStream(TokenStreamData(trees.toList()))

        /**
         * Flattens multiple token streams into a single stream.
         */
        public fun fromTokenStreams(streams: Iterable<TokenStream>): TokenStream {
            val collected = mutableListOf<TokenTree>()
            for (stream in streams) {
                collected.addAll(stream.data.trees)
            }
            return TokenStream(TokenStreamData(collected))
        }
    }

    /** Returns an independent deep copy of this [TokenStream]. */
    public fun clone(): TokenStream = BridgeMethods.tsClone(ClientTokenStream(this)).stream

    /** Checks if this [TokenStream] is empty. */
    public fun isEmpty(): Boolean = data.trees.isEmpty()

    /**
     * Parses this [TokenStream] as an expression and attempts to expand
     * any macros within it, returning the expanded [TokenStream].
     *
     * Returns [ExpandError] when no compiler expansion server is active or
     * when expansion fails.
     */
    public fun expandExpr(): TokenStreamExpandOutcome =
        when (val expanded = BridgeMethods.tsExpandExpr(ClientTokenStream(this))) {
            is Result.Ok -> TokenStreamExpandOutcome.Ok(expanded.value.stream)
            is Result.Err -> TokenStreamExpandOutcome.Err(ExpandError())
        }

    /**
     * Concatenates the given token trees onto this stream.
     */
    public fun extendTokenTrees(trees: Iterable<TokenTree>) {
        data.trees = data.trees + trees.toList()
    }

    /**
     * Concatenates the given token streams onto this stream.
     */
    public fun extendTokenStreams(streams: Iterable<TokenStream>) {
        val collected = mutableListOf<TokenTree>()
        for (stream in streams) {
            collected.addAll(stream.data.trees)
        }
        data.trees = data.trees + collected
    }

    /** Concatenates the given groups onto this stream. */
    public fun extendGroups(groups: Iterable<Group>) {
        extendTokenTrees(groups.map(TokenTree::Group))
    }

    /** Concatenates the given literals onto this stream. */
    public fun extendLiterals(literals: Iterable<Literal>) {
        extendTokenTrees(literals.map(TokenTree::Literal))
    }

    /** Concatenates the given punctuation tokens onto this stream. */
    public fun extendPuncts(punctuation: Iterable<Punct>) {
        extendTokenTrees(punctuation.map(TokenTree::Punct))
    }

    /** Concatenates the given identifiers onto this stream. */
    public fun extendIdents(identifiers: Iterable<Ident>) {
        extendTokenTrees(identifiers.map(TokenTree::Ident))
    }

    /**
     * Iterates the contained [TokenTree] elements shallowly without
     * recursing into delimited [Group] tokens.
     */
    override fun iterator(): IntoIter = IntoIter(data.trees.toList())

    /**
     * Prints the token stream as a string convertible back into the same
     * token stream (modulo spans), except for [TokenTree.Group] tokens with
     * [Delimiter.NONE] delimiters and negative numeric literals.
     */
    override fun toString(): String =
        buildString {
            var previous: TokenTree? = null
            for (tree in data.trees) {
                if (previous != null && needsSeparatorAfter(previous)) {
                    append(' ')
                }
                when (tree) {
                    is TokenTree.Group -> appendGroup(tree.value)
                    is TokenTree.Ident -> append(tree.toString())
                    is TokenTree.Punct -> append(tree.toString())
                    is TokenTree.Literal -> append(tree.toString())
                }
                previous = tree
            }
        }
}

/**
 * Internal backing store for [TokenStream].
 */
internal class TokenStreamData(
    initial: List<TokenTree>,
) {
    internal var trees: List<TokenTree> = initial
}

private fun StringBuilder.appendGroup(group: Group) {
    val (open, close) =
        when (group.delimiter()) {
            Delimiter.PARENTHESIS -> "(" to ")"
            Delimiter.BRACE -> "{ " to " }"
            Delimiter.BRACKET -> "[" to "]"
            Delimiter.NONE -> "" to ""
        }
    append(open)
    append(group.stream().toString())
    append(close)
}

private fun needsSeparatorAfter(left: TokenTree): Boolean {
    if (left is TokenTree.Punct && left.value.spacing() == Spacing.JOINT) return false
    return true
}
