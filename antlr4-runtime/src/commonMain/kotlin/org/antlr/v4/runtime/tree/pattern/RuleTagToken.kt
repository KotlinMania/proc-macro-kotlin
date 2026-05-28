package org.antlr.v4.runtime.tree.pattern

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.TokenSource

class RuleTagToken(ruleName: String, bypassTokenType: Int, label: String?) : Token {
    val ruleName: String
    override val type: Int
    val label: String?

    constructor(ruleName: String, bypassTokenType: Int) : this(ruleName, bypassTokenType, null)

    init {
        require(ruleName.isNotEmpty()) { "ruleName cannot be null or empty." }
        this.ruleName = ruleName
        this.type = bypassTokenType
        this.label = label
    }

    override val channel: Int get() = Token.DEFAULT_CHANNEL

    override val text: String?
        get() {
            if (label != null) return "<$label:$ruleName>"
            return "<$ruleName>"
        }

    override val line: Int get() = 0
    override val charPositionInLine: Int get() = -1
    override val tokenIndex: Int get() = -1
    override val startIndex: Int get() = -1
    override val stopIndex: Int get() = -1
    override val tokenSource: TokenSource? get() = null
    override val inputStream: CharStream? get() = null

    override fun toString(): String = "$ruleName:$type"
}
