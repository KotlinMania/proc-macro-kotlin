package org.antlr.v4.runtime.tree.pattern

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.TokenSource

class TokenTagToken(
    val tokenName: String?,
    override val type: Int,
    val label: String?
) : Token {

    constructor(tokenName: String?, type: Int) : this(tokenName, type, null)

    override val text: String?
        get() {
            if (label != null) return "<$label:$tokenName>"
            return "<$tokenName>"
        }

    override val channel: Int get() = 0
    override val line: Int get() = 0
    override val charPositionInLine: Int get() = -1
    override val tokenIndex: Int get() = -1
    override val startIndex: Int get() = -1
    override val stopIndex: Int get() = -1
    override val tokenSource: TokenSource? get() = null
    override val inputStream: CharStream? get() = null

    override fun toString(): String = "$tokenName:$type"
}
