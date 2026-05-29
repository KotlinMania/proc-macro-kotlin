/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

import org.antlr.v4.runtime.misc.Pair

/**
 * Provides an implementation of [TokenSource] as a wrapper around a list
 * of [Token] objects.
 *
 *
 * If the final token in the list is an [Token.EOF] token, it will be used
 * as the EOF token for every call to [.nextToken] after the end of the
 * list is reached. Otherwise, an EOF token will be created.
 */
class ListTokenSource(
    tokens: List<out Token?>,
    _sourceName: String?,
) : TokenSource {
    /**
     * The wrapped collection of [Token] objects to return.
     */
    protected val tokens: List<out Token?>

    /**
     * The name of the input source. If this value is `null`, the source name
     * is inferred from the next token's input stream.
     */
    private val _sourceName: String? = _sourceName

    override val sourceName: String?
        get() {
            if (_sourceName != null) return _sourceName
            val inputStream: CharStream? = this.inputStream
            if (inputStream != null) return inputStream.sourceName
            return "List"
        }

    /**
     * The index into [.tokens] of token to return by the next call to
     * [.nextToken]. The end of the input is indicated by this value
     * being greater than or equal to the number of items in [.tokens].
     */
    protected var i: Int = 0

    /**
     * This field caches the EOF token for the token source.
     */
    protected var eofToken: Token? = null

    /**
     * This is the backing field for [.getTokenFactory] and
     * [setTokenFactory].
     */
    private var _factory: TokenFactory<*> = CommonTokenFactory.DEFAULT

    /**
     * Constructs a new [ListTokenSource] instance from the specified
     * collection of [Token] objects.
     *
     * @param tokens The collection of [Token] objects to provide as a
     * [TokenSource].
     * @exception NullPointerException if `tokens` is `null`
     */
    constructor(tokens: List<out Token?>) : this(tokens, null)

    /**
     * Constructs a new [ListTokenSource] instance from the specified
     * collection of [Token] objects and source name.
     *
     * @param tokens The collection of [Token] objects to provide as a
     * [TokenSource].
     * @param sourceName The name of the [TokenSource]. If this value is
     * `null`, [.getSourceName] will attempt to infer the name from
     * the next [Token] (or the previous token if the end of the input has
     * been reached).
     *
     * @exception NullPointerException if `tokens` is `null`
     */
    init {
        this.tokens = tokens
        // _sourceName is set via constructor parameter
    }

    override val charPositionInLine: Int
        /**
         * {@inheritDoc}
         */
        get() {
            if (i < tokens.size) {
                return tokens.get(i)!!.charPositionInLine
            } else if (eofToken != null) {
                return eofToken!!.charPositionInLine
            } else if (tokens.size > 0) {
                // have to calculate the result from the line/column of the previous
                // token, along with the text of the token.
                val lastToken: Token = tokens.get(tokens.size - 1)!!
                val tokenText: String? = lastToken.text
                if (tokenText != null) {
                    val lastNewLine: Int = tokenText.lastIndexOf('\n')
                    if (lastNewLine >= 0) {
                        return tokenText.length - lastNewLine - 1
                    }
                }

                return lastToken.charPositionInLine + lastToken.stopIndex - lastToken.startIndex + 1
            }

            // only reach this if tokens is empty, meaning EOF occurs at the first
            // position in the input
            return 0
        }

    /**
     * {@inheritDoc}
     */
    override fun nextToken(): Token? {
        if (i >= tokens.size) {
            if (eofToken == null) {
                var start = -1
                if (tokens.size > 0) {
                    val previousStop: Int = tokens.get(tokens.size - 1)!!.stopIndex
                    if (previousStop != -1) {
                        start = previousStop + 1
                    }
                }

                val stop: Int = maxOf(-1, start - 1)
                eofToken =
                    _factory.create(
                        Pair<TokenSource?, CharStream?>(this, this.inputStream),
                        Token.EOF,
                        "EOF",
                        Token.DEFAULT_CHANNEL,
                        start,
                        stop,
                        this.line,
                        this.charPositionInLine,
                    )
            }

            return eofToken
        }

        val t: Token = tokens.get(i)!!
        if (i == tokens.size - 1 && t.type == Token.EOF) {
            eofToken = t
        }

        i++
        return t
    }

    override val line: Int
        /**
         * {@inheritDoc}
         */
        get() {
            if (i < tokens.size) {
                return tokens.get(i)!!.line
            } else if (eofToken != null) {
                return eofToken!!.line
            } else if (tokens.size > 0) {
                // have to calculate the result from the line/column of the previous
                // token, along with the text of the token.
                val lastToken: Token = tokens.get(tokens.size - 1)!!
                var line: Int = lastToken.line

                val tokenText: String? = lastToken.text
                if (tokenText != null) {
                    for (i in 0..<tokenText.length) {
                        if (tokenText[i] == '\n') {
                            line++
                        }
                    }
                }

                // if no text is available, assume the token did not contain any newline characters.
                return line
            }

            // only reach this if tokens is empty, meaning EOF occurs at the first
            // position in the input
            return 1
        }
    override val inputStream: CharStream?
        /**
         * {@inheritDoc}
         */
        get() {
            if (i < tokens.size) {
                return tokens.get(i)!!.inputStream
            } else if (eofToken != null) {
                return eofToken!!.inputStream
            } else if (tokens.size > 0) {
                return tokens.get(tokens.size - 1)!!.inputStream
            }

            // no input stream information is available
            return null
        }



    /**
     * {@inheritDoc}
     */
    override fun setTokenFactory(factory: TokenFactory<*>?) {
        this._factory = factory!!
    }

    override val tokenFactory: TokenFactory<*>?
        /**
         * {@inheritDoc}
         */
        get() = _factory
}
