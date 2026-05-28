package org.antlr.v4.runtime.tree.xpath

import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.Trees

class XPathRuleAnywhereElement(ruleName: String, val ruleIndex: Int) : XPathElement(ruleName) {
    override fun evaluate(t: ParseTree): Collection<ParseTree> {
        return Trees.findAllRuleNodes(t, ruleIndex)
    }
}
