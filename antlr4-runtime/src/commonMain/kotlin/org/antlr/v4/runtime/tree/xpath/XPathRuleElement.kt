package org.antlr.v4.runtime.tree.xpath

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.Tree
import org.antlr.v4.runtime.tree.Trees

class XPathRuleElement(ruleName: String, val ruleIndex: Int) : XPathElement(ruleName) {
    override fun evaluate(t: ParseTree): Collection<ParseTree?> {
        val nodes = mutableListOf<ParseTree>()
        for (c in Trees.getChildren(t)) {
            if (c is ParserRuleContext) {
                val ctx = c
                if ((ctx.ruleIndex == ruleIndex && !invert) ||
                    (ctx.ruleIndex != ruleIndex && invert)
                ) {
                    nodes.add(ctx)
                }
            }
        }
        return nodes
    }
}
