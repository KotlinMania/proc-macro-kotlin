package org.antlr.v4.runtime.tree.xpath

import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.Trees

class XPathTokenAnywhereElement(tokenName: String, val tokenType: Int) : XPathElement(tokenName) {
    override fun evaluate(t: ParseTree): Collection<ParseTree> {
        return Trees.findAllTokenNodes(t, tokenType)
    }
}
