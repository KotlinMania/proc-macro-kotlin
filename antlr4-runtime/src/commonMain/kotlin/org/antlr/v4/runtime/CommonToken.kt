/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.misc.Pair

class CommonToken : WritableToken, Serializable {
    /**
     * This is the backing field for [.getType] and [.setType].
     */
    @set:Override
    var type: Int

    /**
     * This is the backing field for [.getLine] and [.setLine].
     */
    @set:Override
    var line: Int = 0

    /**
     * This is the backing field for [.getCharPositionInLine] and
     * [.setCharPositionInLine].
     */
    @set:Override
    var charPositionInLine: Int = -1 // set to invalid position

    /**
     * This is the backing field for [.getChannel] and
     * [.setChannel].
     */
    @set:Override
    var channel: Int = DEFAULT_CHANNEL

    /**
     * This is the backing field for [.getTokenSource] and
     * [.getInputStream].
     *
     *
     *
     * These properties share a field to reduce the memory footprint of
     * [CommonToken]. Tokens created by a [CommonTokenFactory] from
     * the same source and input stream share a reference to the same
     * [Pair] containing these values.
     */
    protected var source: Pair<TokenSource?, CharStream?>? = null

    /**
     * This is the backing field for [.getText] when the token text is
     * explicitly set in the constructor or via [.setText].
     *
     * @see .getText
     */
    protected var text: String? = null

    /**
     * This is the backing field for [.getTokenIndex] and
     * [.setTokenIndex].
     */
    @set:Override
    var tokenIndex: Int = -1

    /**
     * This is the backing field for [.getStartIndex] and
     * [.setStartIndex].
     */
    var startIndex: Int = 0

    /**
     * This is the backing field for [.getStopIndex] and
     * [.setStopIndex].
     */
    var stopIndex: Int = 0

    /**
     * Constructs a new [CommonToken] with the specified token type.
     *
     * @param type The token type.
     */
    constructor(type: Int) {
        this.type = type
        this.source = org.antlr.v4.runtime.CommonToken.Companion.EMPTY_SOURCE
    }

    constructor(source: Pair<TokenSource?, CharStream?>, type: Int, channel: Int, start: Int, stop: Int) {
        this.source = source
        this.type = type
        this.channel = channel
        this.startIndex = start
        this.stopIndex = stop
        if (source.a != null) {
            this.line = source.a.line
            this.charPositionInLine = source.a.charPositionInLine
        }
    }

    /**
     * Constructs a new [CommonToken] with the specified token type and
     * text.
     *
     * @param type The token type.
     * @param text The text of the token.
     */
    constructor(type: Int, text: String?) {
        this.type = type
        this.channel = DEFAULT_CHANNEL
        this.text = text
        this.source = org.antlr.v4.runtime.CommonToken.Companion.EMPTY_SOURCE
    }

    /**
     * Constructs a new [CommonToken] as a copy of another [Token].
     *
     *
     *
     * If `oldToken` is also a [CommonToken] instance, the newly
     * constructed token will share a reference to the [.text] field and
     * the [Pair] stored in [.source]. Otherwise, [.text] will
     * be assigned the result of calling [.getText], and [.source]
     * will be constructed from the result of [Token.getTokenSource] and
     * [Token.getInputStream].
     *
     * @param oldToken The token to copy.
     */
    constructor(oldToken: Token) {
        type = oldToken.type
        line = oldToken.line
        this.tokenIndex = oldToken.tokenIndex
        charPositionInLine = oldToken.charPositionInLine
        channel = oldToken.channel
        this.startIndex = oldToken.startIndex
        this.stopIndex = oldToken.stopIndex

        if (oldToken is CommonToken) {
            text = oldToken.text
            source = oldToken.source
        } else {
            text = oldToken.text
            source = Pair<TokenSource?, CharStream?>(oldToken.tokenSource, oldToken.inputStream)
        }
    }
    fun getText(): String? {
        if (text != null) {
            return text
        }

        val input: CharStream? = this.inputStream
        if (input == null) return null
        val n: Int = input.size
        if (this.startIndex < n && this.stopIndex < n) {
            return input.getText(Interval.of(this.startIndex, this.stopIndex))
        } else {
            return "<EOF>"
        }
    }

    /**
     * Explicitly set the text for this token. If {code text} is not
     * `null`, then [.getText] will return this value rather than
     * extracting the text from the input.
     *
     * @param text The explicit text of the token, or `null` if the text
     * should be obtained from the input along with the start and stop indexes
     * of the token.
     */
    fun setText(text: String?) {
        this.text = text
    }
    val tokenSource: TokenSource
        get() = source.a
    val inputStream: CharStream
        get() = source.b
    fun toString(): String? {
        return toString(null)
    }

    fun toString(r: Recognizer<*, *>?): String? {
        var channelStr = ""
        if (channel > 0) {
            channelStr = ",channel=" + channel
        }
        var txt = getText()
        if (txt != null) {
            txt = txt.replace("\n", "\\n")
            txt = txt.replace("\r", "\\r")
            txt = txt.replace("\t", "\\t")
        } else {
            txt = "<no text>"
        }
        var typeString: String? = type.toString()
        if (r != null) {
            typeString = r.vocabulary.getDisplayName(type)
        }
        return "[@" + this.tokenIndex + "," + this.startIndex + ":" + this.stopIndex + "='" + txt + "',<" + typeString + ">" + channelStr + "," + line + ":" + this.charPositionInLine + "]"
    }

    companion object {
        /**
         * An empty [Pair] which is used as the default value of
         * [.source] for tokens that do not have a source.
         */
        protected val EMPTY_SOURCE: Pair<TokenSource?, CharStream?> = Pair<TokenSource?, CharStream?>(null, null)
    }
}
