package org.antlr.v4.runtime.tree

import org.antlr.v4.runtime.Token

class ErrorNodeImpl(
    token: Token?,
) : TerminalNodeImpl(token),
    ErrorNode {
    override fun <T> accept(visitor: ParseTreeVisitor<out T?>?): T? = visitor?.visitErrorNode(this)
}
