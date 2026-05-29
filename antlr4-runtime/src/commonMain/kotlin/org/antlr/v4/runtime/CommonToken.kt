/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.misc.Pair

class CommonToken : WritableToken {
    override var type: Int

    override var line: Int = 0

    override var charPositionInLine: Int = -1

    override var channel: Int = Token.DEFAULT_CHANNEL

    protected var source: Pair<TokenSource?, CharStream?>? = null

    private var _text: String? = null

    override var text: String?
        get() {
            if (_text != null) {
                return _text
            }
            val input: CharStream? = this.inputStream
            if (input == null) return null
            val n: Int = input.size()
            if (this.startIndex < n && this.stopIndex < n) {
                return input.getText(Interval.of(this.startIndex, this.stopIndex))
            } else {
                return "<EOF>"
            }
        }
        set(value) {
            _text = value
        }

    override var tokenIndex: Int = -1

    override var startIndex: Int = 0

    override var stopIndex: Int = 0

    constructor(type: Int) {
        this.type = type
        this.source = CommonToken.EMPTY_SOURCE
    }

    constructor(source: Pair<TokenSource?, CharStream?>, type: Int, channel: Int, start: Int, stop: Int) {
        this.source = source
        this.type = type
        this.channel = channel
        this.startIndex = start
        this.stopIndex = stop
        if (source.a != null) {
            this.line = source.a?.line ?: 0
            this.charPositionInLine = source.a?.charPositionInLine ?: -1
        }
    }

    constructor(type: Int, text: String?) {
        this.type = type
        this.channel = Token.DEFAULT_CHANNEL
        this._text = text
        this.source = CommonToken.EMPTY_SOURCE
    }

    constructor(oldToken: Token) {
        type = oldToken.type
        line = oldToken.line
        this.tokenIndex = oldToken.tokenIndex
        charPositionInLine = oldToken.charPositionInLine
        channel = oldToken.channel
        this.startIndex = oldToken.startIndex
        this.stopIndex = oldToken.stopIndex

        if (oldToken is CommonToken) {
            _text = oldToken._text
            source = oldToken.source
        } else {
            _text = oldToken.text
            source = Pair<TokenSource?, CharStream?>(oldToken.tokenSource, oldToken.inputStream)
        }
    }

    override val tokenSource: TokenSource?
        get() = source?.a
    override val inputStream: CharStream?
        get() = source?.b

    override fun toString(): String = toString(null)

    fun toString(r: Recognizer<*, *>?): String {
        var channelStr = ""
        if (channel > 0) {
            channelStr = ",channel=$channel"
        }
        var txt = text
        if (txt != null) {
            txt = txt.replace("\n", "\\n")
            txt = txt.replace("\r", "\\r")
            txt = txt.replace("\t", "\\t")
        } else {
            txt = "<no text>"
        }
        var typeString: String = type.toString()
        if (r != null) {
            typeString = r.vocabulary.getDisplayName(type)!!
        }
        return "[@${this.tokenIndex},${this.startIndex}:${this.stopIndex}='$txt',<$typeString>$channelStr,$line:${this.charPositionInLine}]"
    }

    companion object {
        protected val EMPTY_SOURCE: Pair<TokenSource?, CharStream?> = Pair<TokenSource?, CharStream?>(null, null)
    }
}
