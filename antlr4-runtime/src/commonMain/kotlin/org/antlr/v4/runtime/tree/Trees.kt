package org.antlr.v4.runtime.tree

import org.antlr.v4.runtime.CommonToken
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.atn.ATN
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.misc.Predicate
import org.antlr.v4.runtime.misc.Utils
import org.antlr.v4.runtime.tree.Trees.toStringTree

object Trees {
    fun toStringTree(t: Tree): String? {
        return toStringTree(t, null as List<String?>?)
    }

    fun toStringTree(t: Tree, recog: Parser?): String? {
        val ruleNamesList: List<String?>? = recog?.ruleNames?.toList()
        return toStringTree(t, ruleNamesList)
    }

    fun toStringTree(t: Tree, ruleNames: List<String?>?): String? {
        var s: String = Utils.escapeWhitespace(getNodeText(t, ruleNames) ?: "null", false)
        if (t.childCount === 0) return s
        val buf: StringBuilder = StringBuilder()
        buf.append("(")
        s = Utils.escapeWhitespace(getNodeText(t, ruleNames) ?: "null", false)
        buf.append(s)
        buf.append(' ')
        for (i in 0..<t.childCount) {
            if (i > 0) buf.append(' ')
            val child = t.getChild(i) ?: continue
            buf.append(toStringTree(child, ruleNames))
        }
        buf.append(")")
        return buf.toString()
    }

    fun getNodeText(t: Tree, recog: Parser?): String? {
        val ruleNamesList: List<String?>? = recog?.ruleNames?.toList()
        return getNodeText(t, ruleNamesList)
    }

    fun getNodeText(t: Tree, ruleNames: List<String?>?): String? {
        if (ruleNames != null) {
            if (t is RuleContext) {
                val ruleIndex: Int = t.ruleContext.ruleIndex
                val ruleName: String = ruleNames[ruleIndex] ?: ""
                val altNumber: Int = t.altNumber
                if (altNumber != ATN.INVALID_ALT_NUMBER) {
                    return "$ruleName:$altNumber"
                }
                return ruleName
            } else if (t is ErrorNode) {
                return t.toString()
            } else if (t is TerminalNode) {
                val symbol: Token? = t.symbol
                if (symbol != null) {
                    val s: String? = symbol.text
                    return s
                }
            }
        }
        val payload: Any? = t.payload
        if (payload is Token) {
            return payload.text
        }
        return t.payload.toString()
    }

    fun getChildren(t: Tree): List<Tree?> {
        val kids: MutableList<Tree?> = ArrayList()
        for (i in 0..<t.childCount) {
            kids.add(t.getChild(i))
        }
        return kids
    }

    fun getAncestors(t: Tree?): List<out Tree?> {
        var t: Tree? = t
        if (t?.parent == null) return emptyList()
        val ancestors: MutableList<Tree?> = ArrayList()
        t = t.parent
        while (t != null) {
            ancestors.add(0, t)
            t = t.parent
        }
        return ancestors
    }

    fun isAncestorOf(t: Tree?, u: Tree?): Boolean {
        if (t == null || u == null || t.parent == null) return false
        var p: Tree? = u.parent
        while (p != null) {
            if (t === p) return true
            p = p.parent
        }
        return false
    }

    fun findAllTokenNodes(t: ParseTree, ttype: Int): Collection<ParseTree?> {
        return findAllNodes(t, ttype, true)
    }

    fun findAllRuleNodes(t: ParseTree, ruleIndex: Int): Collection<ParseTree?> {
        return findAllNodes(t, ruleIndex, false)
    }

    fun findAllNodes(t: ParseTree, index: Int, findTokens: Boolean): List<ParseTree?> {
        val nodes: MutableList<ParseTree?> = ArrayList()
        _findAllNodes(t, index, findTokens, nodes)
        return nodes
    }

    fun _findAllNodes(
        t: ParseTree, index: Int, findTokens: Boolean,
        nodes: MutableList<in ParseTree?>
    ) {
        if (findTokens && t is TerminalNode) {
            val tnode: TerminalNode = t
            if (tnode.symbol?.type === index) nodes.add(t)
        } else if (!findTokens && t is ParserRuleContext) {
            val ctx: ParserRuleContext = t
            if (ctx.ruleIndex === index) nodes.add(t)
        }
        for (i in 0..<t.childCount) {
            val child = t.getChild(i) ?: continue
            _findAllNodes(child, index, findTokens, nodes)
        }
    }

    fun getDescendants(t: ParseTree): List<ParseTree?> {
        val nodes: MutableList<ParseTree?> = ArrayList()
        nodes.add(t)

        val n: Int = t.childCount
        for (i in 0..<n) {
            val child = t.getChild(i) ?: continue
            nodes.addAll(getDescendants(child))
        }
        return nodes
    }

    @Deprecated("")
    fun descendants(t: ParseTree): List<ParseTree?> {
        return getDescendants(t)
    }

    fun getRootOfSubtreeEnclosingRegion(
        t: ParseTree,
        startTokenIndex: Int,
        stopTokenIndex: Int
    ): ParserRuleContext? {
        val n: Int = t.childCount
        for (i in 0..<n) {
            val child = t.getChild(i) ?: continue
            val r: ParserRuleContext? =
                getRootOfSubtreeEnclosingRegion(child, startTokenIndex, stopTokenIndex)
            if (r != null) return r
        }
        if (t is ParserRuleContext) {
            val r: ParserRuleContext = t
            val startToken = r.getStart()
            if (startToken != null && startTokenIndex >= startToken.tokenIndex &&
                (r.getStop() == null || stopTokenIndex <= r.getStop()!!.tokenIndex)
            ) {
                return r
            }
        }
        return null
    }

    fun stripChildrenOutOfRange(
        t: ParserRuleContext?,
        root: ParserRuleContext?,
        startIndex: Int,
        stopIndex: Int
    ) {
        if (t == null) return
        for (i in 0..<t.childCount) {
            val child = t.getChild(i) ?: continue
            val range: Interval = child.sourceInterval ?: continue
            if (child is ParserRuleContext && (range.b < startIndex || range.a > stopIndex)) {
                if (isAncestorOf(child, root)) {
                    val abbrev: CommonToken = CommonToken(Token.INVALID_TYPE, "...")
                    t.children?.set(i, TerminalNodeImpl(abbrev))
                }
            }
        }
    }

    fun findNodeSuchThat(t: Tree?, pred: Predicate<Tree?>): Tree? {
        if (pred.test(t)) return t

        if (t == null) return null

        val n: Int = t.childCount
        for (i in 0..<n) {
            val u: Tree? = findNodeSuchThat(t.getChild(i), pred)
            if (u != null) return u
        }
        return null
    }
}
