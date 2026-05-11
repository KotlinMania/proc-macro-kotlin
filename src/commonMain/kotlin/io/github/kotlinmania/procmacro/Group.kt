// port-lint: source src/lib.rs
package io.github.kotlinmania.procmacro

/**
 * A delimited token stream.
 *
 * A [Group] internally contains a [TokenStream] which is surrounded by
 * [Delimiter]s.
 */
public class Group internal constructor(
    internal val data: GroupData,
) {
    public companion object {
        /**
         * Creates a new [Group] with the given delimiter and token stream.
         *
         * This constructor will set the span for this group to
         * [Span.callSite]. To change the span you can use the [setSpan]
         * method below.
         */
        public fun new(delimiter: Delimiter, stream: TokenStream): Group =
            Group(
                GroupData(
                    delimiter = delimiter,
                    stream = stream,
                    span = DelimSpanData.fromSingle(Span.callSite()),
                ),
            )
    }

    /** Returns the delimiter of this [Group]. */
    public fun delimiter(): Delimiter = data.delimiter

    /**
     * Returns the [TokenStream] of tokens that are delimited in this
     * [Group].
     *
     * Note that the returned token stream does not include the delimiter
     * returned above.
     */
    public fun stream(): TokenStream = data.stream

    /**
     * Returns the span for the delimiters of this token stream, spanning
     * the entire [Group].
     *
     * ```
     * fun span(): Span = ...
     * //  ^^^^^^^
     * ```
     */
    public fun span(): Span = data.span.entire

    /**
     * Returns the span pointing to the opening delimiter of this group.
     *
     * ```
     * fun spanOpen(): Span = ...
     * //  ^
     * ```
     */
    public fun spanOpen(): Span = data.span.open

    /**
     * Returns the span pointing to the closing delimiter of this group.
     *
     * ```
     * fun spanClose(): Span = ...
     * //  ^
     * ```
     */
    public fun spanClose(): Span = data.span.close

    /**
     * Configures the span for this [Group]'s delimiters, but not its
     * internal tokens.
     *
     * This method will not set the span of all the internal tokens
     * spanned by this group, but rather it will only set the span of the
     * delimiter tokens at the level of the [Group].
     */
    public fun setSpan(span: Span) {
        data.span = DelimSpanData.fromSingle(span)
    }

    /**
     * Prints the group as a string that should be losslessly convertible
     * back into the same group (modulo spans), except for possibly
     * [TokenTree.Group]s with [Delimiter.NONE] delimiters.
     */
    override fun toString(): String =
        TokenStream.fromTokenTree(TokenTree.Group(this)).toString()
}

/**
 * Internal backing store for [Group].
 *
 * Upstream: `bridge::Group<bridge::client::TokenStream, bridge::client::Span>`.
 */
internal data class GroupData(
    val delimiter: Delimiter,
    val stream: TokenStream,
    var span: DelimSpanData,
)
