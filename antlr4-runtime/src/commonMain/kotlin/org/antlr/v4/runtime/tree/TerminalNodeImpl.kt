/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.tree

import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.misc.Interval

class TerminalNodeImpl(symbol: Token?) : TerminalNode {
    var symbol: Token?
    var parent: ParseTree? = null

    init {
        this.symbol = symbol
    }
    fun getChild(i: Int): ParseTree? {
        return null
    }
    fun getSymbol(): Token? {
        return symbol
    }
    fun getParent(): ParseTree? {
        return parent
    }
    fun setParent(parent: RuleContext?) {
        this.parent = parent
    }
    val payload: Token?
        get() = symbol
    val sourceInterval: Interval?
        get() {
            if (symbol == null) return Interval.INVALID

            val tokenIndex: Int = symbol.tokenIndex
            return Interval(tokenIndex, tokenIndex)
        }
    val childCount: Int
        get() = 0
    fun <T> accept(visitor: ParseTreeVisitor<out T?>): T? {
        return visitor.visitTerminal(this)
    }
    val text: String
        get() = symbol.text
    fun toStringTree(parser: Parser?): String? {
        return toString()
    }
    fun toString(): String? {
        if (symbol.type === Token.EOF) return "<EOF>"
        return symbol.text
    }
    fun toStringTree(): String? {
        return toString()
    }
}
