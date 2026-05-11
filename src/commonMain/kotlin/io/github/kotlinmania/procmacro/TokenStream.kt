// port-lint: source src/lib.rs
package io.github.kotlinmania.procmacro

import io.github.kotlinmania.procmacro.tokenstream.IntoIter

/**
 * The main type provided by this crate, representing an abstract stream
 * of tokens, or, more specifically, a sequence of token trees. The type
 * provides interfaces for iterating over those token trees and,
 * conversely, collecting a number of token trees into one stream.
 *
 * This is both the input and output of `@ProcMacro`, `@ProcMacroAttribute`
 * and `@ProcMacroDerive` definitions.
 */
public class TokenStream internal constructor(
    internal val data: TokenStreamData,
) : Iterable<TokenTree> {
    public companion object {
        /** Returns an empty [TokenStream] containing no token trees. */
        public fun new(): TokenStream = TokenStream(TokenStreamData(emptyList()))

        /**
         * Creates a token stream containing a single token tree.
         *
         * Upstream: `impl From<TokenTree> for TokenStream`.
         */
        public fun fromTokenTree(tree: TokenTree): TokenStream =
            TokenStream(TokenStreamData(listOf(tree)))

        /**
         * Attempts to break the string into tokens and parse those tokens
         * into a token stream. May fail for a number of reasons, for
         * example, if the string contains unbalanced delimiters or
         * characters not existing in the language. All tokens in the
         * parsed stream get [Span.callSite] spans.
         *
         * Phase-1 stub throws [LexError]. Phase 3 wires this to the
         * vendored
         * [`KotlinLexer`][org.jetbrains.kotlin.kmp.lexer.KotlinLexer] so
         * lexing real Kotlin source produces proper token trees.
         */
        public fun fromString(src: String): Result<TokenStream> {
            return Result.failure(
                LexErrorThrown(
                    LexError(
                        "TokenStream.fromString is not implemented yet; " +
                            "the KotlinLexer backend is wired in during phase 3 " +
                            "of the proc-macro-kotlin port (see DESIGN.md).",
                    ),
                ),
            )
        }

        /**
         * Collects a number of token trees into a single stream.
         *
         * Upstream: `impl FromIterator<TokenTree> for TokenStream`.
         */
        public fun fromTokenTrees(trees: Iterable<TokenTree>): TokenStream =
            TokenStream(TokenStreamData(trees.toList()))

        /**
         * A "flattening" operation on token streams: collects token trees
         * from multiple token streams into a single stream.
         *
         * Upstream: `impl FromIterator<TokenStream> for TokenStream`.
         */
        public fun fromTokenStreams(streams: Iterable<TokenStream>): TokenStream {
            val collected = mutableListOf<TokenTree>()
            for (stream in streams) {
                collected.addAll(stream.data.trees)
            }
            return TokenStream(TokenStreamData(collected))
        }
    }

    /** Checks if this [TokenStream] is empty. */
    public fun isEmpty(): Boolean = data.trees.isEmpty()

    /**
     * Parses this [TokenStream] as an expression and attempts to expand
     * any macros within it. Returns the expanded [TokenStream].
     *
     * Currently only expressions expanding to literals will succeed,
     * although this may be relaxed in the future. The phase-1 stub
     * always returns [ExpandError]; macro expansion has no Kotlin
     * analog at this stage.
     */
    public fun expandExpr(): Result<TokenStream> = Result.failure(ExpandErrorThrown(ExpandError()))

    /**
     * Concatenates the given token trees into this stream.
     *
     * Upstream: `impl Extend<TokenTree> for TokenStream`. The Kotlin port
     * makes this an explicit member function rather than relying on an
     * `Extend`-equivalent trait, since the workspace forbids `MutableList`
     * monkey-patching on internal data.
     */
    public fun extendTokenTrees(trees: Iterable<TokenTree>) {
        data.trees = data.trees + trees.toList()
    }

    /**
     * Concatenates the given token streams onto this stream.
     *
     * Upstream: `impl Extend<TokenStream> for TokenStream`.
     */
    public fun extendTokenStreams(streams: Iterable<TokenStream>) {
        val collected = mutableListOf<TokenTree>()
        for (stream in streams) {
            collected.addAll(stream.data.trees)
        }
        data.trees = data.trees + collected
    }

    /**
     * Iterates the contained [TokenTree]s shallowly: the iterator does
     * not recurse into delimited [Group]s and returns whole groups as
     * single token trees.
     */
    override fun iterator(): IntoIter = IntoIter(data.trees.iterator())

    /**
     * Prints the token stream as a string that is supposed to be
     * losslessly convertible back into the same token stream (modulo
     * spans), except for possibly [TokenTree.Group]s with [Delimiter.NONE]
     * delimiters and negative numeric literals.
     *
     * Phase-1 implementation produces a space-separated rendering of the
     * contained token trees with delimiter pairs around groups. Phase 3
     * may refine the whitespace strategy once KotlinLexer roundtripping
     * is exercised.
     */
    override fun toString(): String = buildString {
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
 *
 * Upstream: `Option<bridge::client::TokenStream>` wrapping a sequence of
 * `bridge::TokenTree`. The Kotlin port keeps a plain `List<TokenTree>`,
 * mutable through [TokenStream.extendTokenTrees] /
 * [TokenStream.extendTokenStreams].
 */
internal class TokenStreamData(initial: List<TokenTree>) {
    internal var trees: List<TokenTree> = initial
}

/**
 * Class wrapper thrown when [TokenStream.fromString] cannot produce a
 * stream. Holds the [LexError] for downstream inspection.
 */
public class LexErrorThrown internal constructor(public val error: LexError) :
    RuntimeException(error.toString())

/**
 * Class wrapper thrown when [TokenStream.expandExpr] fails.
 */
public class ExpandErrorThrown internal constructor(public val error: ExpandError) :
    RuntimeException(error.toString())

/**
 * Lossless string rendering helper for [TokenStream.toString]. Open
 * delimiter and the start of the contained stream, then recurse, then
 * close delimiter. [Delimiter.NONE] groups render their stream verbatim
 * without surrounding characters, matching the upstream behavior described
 * on the [Delimiter.NONE] doc.
 */
private fun StringBuilder.appendGroup(group: Group) {
    val (open, close) = when (group.delimiter()) {
        Delimiter.PARENTHESIS -> "(" to ")"
        Delimiter.BRACE -> "{ " to " }"
        Delimiter.BRACKET -> "[" to "]"
        Delimiter.NONE -> "" to ""
    }
    append(open)
    append(group.stream().toString())
    append(close)
}

/**
 * Returns `true` if a separator (single space) should be inserted after
 * the given [TokenTree] when rendering for the phase-1
 * [TokenStream.toString]. The heuristic mirrors what `proc_macro2`'s
 * fallback Display does: insert a separator unless the token is
 * [TokenTree.Punct] with [Spacing.JOINT] spacing.
 */
private fun needsSeparatorAfter(left: TokenTree): Boolean {
    if (left is TokenTree.Punct && left.value.spacing() == Spacing.JOINT) return false
    return true
}
