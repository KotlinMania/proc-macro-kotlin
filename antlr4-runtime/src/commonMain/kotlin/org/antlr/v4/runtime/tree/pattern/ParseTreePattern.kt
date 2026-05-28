package org.antlr.v4.runtime.tree.pattern

import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.xpath.XPath

class ParseTreePattern(
    matcher: ParseTreePatternMatcher,
    pattern: String?,
    patternRuleIndex: Int,
    patternTree: ParseTree?
) {
    val patternRuleIndex: Int
    val pattern: String?
    private val patternTree: ParseTree?
    private val matcher: ParseTreePatternMatcher

    init {
        this.matcher = matcher
        this.patternRuleIndex = patternRuleIndex
        this.pattern = pattern
        this.patternTree = patternTree
    }

    fun match(tree: ParseTree?): ParseTreeMatch =
        matcher.match(tree, this)

    fun matches(tree: ParseTree?): Boolean =
        matcher.match(tree, this).succeeded()

    fun findAll(tree: ParseTree?, xpath: String?): List<ParseTreeMatch> {
        val subtrees = XPath.findAll(tree, xpath, matcher.getParser())
        val matches = mutableListOf<ParseTreeMatch>()
        for (t in subtrees) {
            val match = match(t)
            if (match.succeeded()) {
                matches.add(match)
            }
        }
        return matches
    }

    fun getMatcher(): ParseTreePatternMatcher = matcher

    fun getPatternTree(): ParseTree? = patternTree
}
