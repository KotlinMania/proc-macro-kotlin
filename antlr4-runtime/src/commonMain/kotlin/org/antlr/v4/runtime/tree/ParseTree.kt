package org.antlr.v4.runtime.tree

import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.RuleContext

interface ParseTree : SyntaxTree {
    override val parent: ParseTree?

    override fun getChild(i: Int): ParseTree?

    fun setParent(parent: RuleContext?)

    fun <T> accept(visitor: ParseTreeVisitor<out T?>?): T?

    val text: String?

    fun toStringTree(parser: Parser?): String?
}
