package org.antlr.v4.runtime.tree.xpath

import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.Tree
import org.antlr.v4.runtime.tree.Trees

class XPathWildcardElement : XPathElement(WILDCARD) {
    companion object {
        const val WILDCARD: String = "*"
    }

    override fun evaluate(t: ParseTree): Collection<ParseTree?> {
        if (invert) return mutableListOf()
        val kids = mutableListOf<ParseTree>()
        for (c in Trees.getChildren(t)) {
            if (c is ParseTree) {
                kids.add(c)
            }
        }
        return kids
    }
}
