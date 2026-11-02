package io.github.kotlinmania.procmacro

import io.github.kotlinmania.procmacro.bridge.BridgeMethods
import io.github.kotlinmania.procmacro.bridge.ClientTokenStream

/**
 * A delimited token stream.
 *
 * A [Group] internally contains a [TokenStream] surrounded by [Delimiter]s.
 */
public class Group internal constructor(
    internal val data: GroupData,
) {
    public companion object {
        /**
         * Creates a new [Group] with the given delimiter and token stream.
         *
         * This constructor sets the span for this group to [Span.callSite].
         * To change the span, use [setSpan].
         */
        public fun new(
            delimiter: Delimiter,
            stream: TokenStream,
        ): Group =
            Group(
                GroupData(
                    delimiter = delimiter,
                    stream = stream,
                    span = DelimSpanData.fromSingle(Span.callSite()),
                ),
            )
    }

    /** Returns an independent deep copy of this [Group]. */
    public fun clone(): Group =
        Group(
            GroupData(
                delimiter = data.delimiter,
                stream = BridgeMethods.tsClone(ClientTokenStream(data.stream)).stream,
                span = data.span.copy(),
            ),
        )

    /** Returns the delimiter of this [Group]. */
    public fun delimiter(): Delimiter = data.delimiter

    /**
     * Returns the [TokenStream] of tokens delimited in this [Group],
     * excluding the surrounding delimiters.
     */
    public fun stream(): TokenStream = BridgeMethods.tsClone(ClientTokenStream(data.stream)).stream

    /**
     * Returns the span encompassing the entire [Group] including delimiters.
     */
    public fun span(): Span = data.span.entire

    /**
     * Returns the span pointing to the opening delimiter of this group.
     */
    public fun spanOpen(): Span = data.span.open

    /**
     * Returns the span pointing to the closing delimiter of this group.
     */
    public fun spanClose(): Span = data.span.close

    /**
     * Configures the span for this [Group]'s delimiters, without altering
     * the spans of internal tokens.
     */
    public fun setSpan(span: Span) {
        data.span = DelimSpanData.fromSingle(span)
    }

    /**
     * Prints the group as a string convertible back into the same group
     * (modulo spans), except for [Delimiter.NONE] groups.
     */
    override fun toString(): String = TokenStream.fromTokenTree(TokenTree.Group(this)).toString()
}

/**
 * Internal backing store for [Group].
 */
internal data class GroupData(
    val delimiter: Delimiter,
    val stream: TokenStream,
    var span: DelimSpanData,
)
