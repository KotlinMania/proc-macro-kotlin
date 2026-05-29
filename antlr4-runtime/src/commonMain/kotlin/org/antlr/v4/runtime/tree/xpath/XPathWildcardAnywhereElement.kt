package org.antlr.v4.runtime.tree.xpath

import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.Trees

class XPathWildcardAnywhereElement : XPathElement(WILDCARD) {
    companion object {
        const val WILDCARD: String = "*"
    }

    override fun evaluate(t: ParseTree): Collection<ParseTree?> {
        if (invert) return mutableListOf()
        return Trees.getDescendants(t)
    }
}
