package org.antlr.v4.runtime.tree.xpath

import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import org.antlr.v4.runtime.tree.Tree
import org.antlr.v4.runtime.tree.Trees

class XPathTokenElement(tokenName: String, val tokenType: Int) : XPathElement(tokenName) {
    override fun evaluate(t: ParseTree): Collection<ParseTree> {
        val nodes = mutableListOf<ParseTree>()
        for (c in Trees.getChildren(t)) {
            if (c is TerminalNode) {
                val tnode = c
                if ((tnode.symbol.type == tokenType && !invert) ||
                    (tnode.symbol.type != tokenType && invert)
                ) {
                    nodes.add(tnode)
                }
            }
        }
        return nodes
    }
}
