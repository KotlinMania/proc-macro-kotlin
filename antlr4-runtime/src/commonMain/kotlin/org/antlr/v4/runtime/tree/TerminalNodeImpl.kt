package org.antlr.v4.runtime.tree

import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.misc.Interval

open class TerminalNodeImpl(override var symbol: Token?) : TerminalNode {
    override var parent: ParseTree? = null

    override fun getChild(i: Int): ParseTree? = null

    override fun setParent(parent: RuleContext?) {
        this.parent = parent
    }

    override val payload: Token?
        get() = symbol

    override val sourceInterval: Interval?
        get() {
            if (symbol == null) return Interval.INVALID
            val tokenIndex: Int = symbol!!.tokenIndex
            return Interval(tokenIndex, tokenIndex)
        }

    override val childCount: Int get() = 0

    override fun <T> accept(visitor: ParseTreeVisitor<out T?>?): T? {
        return visitor?.visitTerminal(this)
    }

    override val text: String?
        get() = symbol?.text

    override fun toStringTree(parser: Parser?): String {
        return toString()
    }

    override fun toStringTree(): String {
        return toString()
    }

    override fun toString(): String {
        if (symbol?.type === Token.EOF) return "<EOF>"
        return symbol?.text ?: "<null>"
    }
}
