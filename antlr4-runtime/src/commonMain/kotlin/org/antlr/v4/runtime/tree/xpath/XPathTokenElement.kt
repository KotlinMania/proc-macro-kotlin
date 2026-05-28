package org.antlr.v4.runtime.tree.xpath

import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import org.antlr.v4.runtime.tree.Tree
import org.antlr.v4.runtime.tree.Trees

class XPathTokenElement(tokenName: String, val tokenType: Int) : XPathElement(tokenName) {
    override fun evaluate(t: ParseTree): Collection<ParseTree?> {
        val nodes = mutableListOf<ParseTree>()
        for (c in Trees.getChildren(t)) {
            if (c is TerminalNode) {
                val tnode: TerminalNode = c
                val tokenTypeMatch = tnode.symbol?.type == tokenType
                if ((tokenTypeMatch && !invert) ||
                    (!tokenTypeMatch && invert)
                ) {
                    nodes.add(tnode)
                }
            }
        }
        return nodes
    }
}
