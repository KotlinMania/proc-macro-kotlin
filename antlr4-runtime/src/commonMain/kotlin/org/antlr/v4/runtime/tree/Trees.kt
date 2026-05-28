/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.tree
import java.util.Arrays
import java.util.Collections

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

/** A set of utility routines useful for all kinds of ANTLR trees.  */
object Trees {
    /** Print out a whole tree in LISP form. [.getNodeText] is used on the
     * node payloads to get the text for the nodes.  Detect
     * parse trees and extract data appropriately.
     */
    fun toStringTree(t: Tree): String? {
        return org.antlr.v4.runtime.tree.Trees.toStringTree(t, null as List<String?>?)
    }

    /** Print out a whole tree in LISP form. [.getNodeText] is used on the
     * node payloads to get the text for the nodes.  Detect
     * parse trees and extract data appropriately.
     */
    fun toStringTree(t: Tree, recog: Parser?): String? {
        val ruleNames: Array<String?>? = if (recog != null) recog.ruleNames else null
        val ruleNamesList: List<String?>? = if (ruleNames != null) ruleNames.toList() else null
        return org.antlr.v4.runtime.tree.Trees.toStringTree(t, ruleNamesList)
    }

    /** Print out a whole tree in LISP form. [.getNodeText] is used on the
     * node payloads to get the text for the nodes.
     */
    fun toStringTree(t: Tree, ruleNames: List<String?>?): String? {
        var s: String? = Utils.escapeWhitespace(org.antlr.v4.runtime.tree.Trees.getNodeText(t, ruleNames), false)
        if (t.childCount === 0) return s
        val buf: StringBuilder = StringBuilder()
        buf.append("(")
        s = Utils.escapeWhitespace(org.antlr.v4.runtime.tree.Trees.getNodeText(t, ruleNames), false)
        buf.append(s)
        buf.append(' ')
        for (i in 0..<t.childCount) {
            if (i > 0) buf.append(' ')
            buf.append(toStringTree(t.getChild(i), ruleNames))
        }
        buf.append(")")
        return buf.toString()
    }

    fun getNodeText(t: Tree, recog: Parser?): String? {
        val ruleNames: Array<String?>? = if (recog != null) recog.ruleNames else null
        val ruleNamesList: List<String?>? = if (ruleNames != null) ruleNames.toList() else null
        return org.antlr.v4.runtime.tree.Trees.getNodeText(t, ruleNamesList)
    }

    fun getNodeText(t: Tree, ruleNames: List<String?>?): String? {
        if (ruleNames != null) {
            if (t is RuleContext) {
                val ruleIndex: Int = (t as RuleContext).ruleContext.ruleIndex
                val ruleName = ruleNames.get(ruleIndex)
                val altNumber: Int = (t as RuleContext).getAltNumber()
                if (altNumber != ATN.INVALID_ALT_NUMBER) {
                    return ruleName.toString() + ":" + altNumber
                }
                return ruleName
            } else if (t is ErrorNode) {
                return t.toString()
            } else if (t is TerminalNode) {
                val symbol: Token? = (t as TerminalNode).symbol
                if (symbol != null) {
                    val s: String? = symbol.text
                    return s
                }
            }
        }
        // no recog for rule names
        val payload: Any = t.payload
        if (payload is Token) {
            return (payload as Token).text
        }
        return t.payload.toString()
    }

    /** Return ordered list of all children of this node  */
    fun getChildren(t: Tree): List<Tree?> {
        val kids: MutableList<Tree> = ArrayList()
        for (i in 0..<t.childCount) {
            kids.add(t.getChild(i))
        }
        return kids
    }

    /** Return a list of all ancestors of this node.  The first node of
     * list is the root and the last is the parent of this node.
     *
     * @since 4.5.1
     */
    fun getAncestors(t: Tree?): List<out Tree?> {
        var t: Tree? = t
        if (t.parent == null) return emptyList()
        val ancestors: MutableList<Tree> = ArrayList()
        t = t.parent
        while (t != null) {
            ancestors.add(0, t) // insert at start
            t = t.parent
        }
        return ancestors
    }

    /** Return true if t is u's parent or a node on path to root from u.
     * Use == not equals().
     *
     * @since 4.5.1
     */
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
        return org.antlr.v4.runtime.tree.Trees.findAllNodes(t, ttype, true)
    }

    fun findAllRuleNodes(t: ParseTree, ruleIndex: Int): Collection<ParseTree?> {
        return org.antlr.v4.runtime.tree.Trees.findAllNodes(t, ruleIndex, false)
    }

    fun findAllNodes(t: ParseTree, index: Int, findTokens: Boolean): List<ParseTree?> {
        val nodes: MutableList<ParseTree> = ArrayList()
        org.antlr.v4.runtime.tree.Trees._findAllNodes(t, index, findTokens, nodes)
        return nodes
    }

    fun _findAllNodes(
        t: ParseTree, index: Int, findTokens: Boolean,
        nodes: List<in ParseTree?>
    ) {
        // check this node (the root) first
        if (findTokens && t is TerminalNode) {
            val tnode: TerminalNode = t as TerminalNode
            if (tnode.symbol.type === index) nodes.add(t)
        } else if (!findTokens && t is ParserRuleContext) {
            val ctx: ParserRuleContext = t as ParserRuleContext
            if (ctx.ruleIndex === index) nodes.add(t)
        }
        // check children
        for (i in 0..<t.childCount) {
            org.antlr.v4.runtime.tree.Trees._findAllNodes(t.getChild(i), index, findTokens, nodes)
        }
    }

    /** Get all descendents; includes t itself.
     *
     * @since 4.5.1
     */
    fun getDescendants(t: ParseTree): List<ParseTree?> {
        val nodes: MutableList<ParseTree> = ArrayList()
        nodes.add(t)

        val n: Int = t.childCount
        for (i in 0..<n) {
            nodes.addAll(org.antlr.v4.runtime.tree.Trees.getDescendants(t.getChild(i)))
        }
        return nodes
    }

    @Deprecated
    @Deprecated("")
    fun descendants(t: ParseTree): List<ParseTree?> {
        return org.antlr.v4.runtime.tree.Trees.getDescendants(t)
    }

    /** Find smallest subtree of t enclosing range startTokenIndex..stopTokenIndex
     * inclusively using postorder traversal.  Recursive depth-first-search.
     *
     * @since 4.5.1
     */
    fun getRootOfSubtreeEnclosingRegion(
        t: ParseTree,
        startTokenIndex: Int,  // inclusive
        stopTokenIndex: Int
    ): ParserRuleContext? // inclusive
    {
        val n: Int = t.childCount
        for (i in 0..<n) {
            val child: ParseTree = t.getChild(i)
            val r: ParserRuleContext? =
                org.antlr.v4.runtime.tree.Trees.getRootOfSubtreeEnclosingRegion(child, startTokenIndex, stopTokenIndex)
            if (r != null) return r
        }
        if (t is ParserRuleContext) {
            val r: ParserRuleContext = t as ParserRuleContext
            if (startTokenIndex >= r.getStart().tokenIndex &&  // is range fully contained in t?
                (r.getStop() == null || stopTokenIndex <= r.getStop().tokenIndex)
            ) {
                // note: r.getStop()==null likely implies that we bailed out of parser and there's nothing to the right
                return r
            }
        }
        return null
    }

    /** Replace any subtree siblings of root that are completely to left
     * or right of lookahead range with a CommonToken(Token.INVALID_TYPE,"...")
     * node. The source interval for t is not altered to suit smaller range!
     *
     * WARNING: destructive to t.
     *
     * @since 4.5.1
     */
    fun stripChildrenOutOfRange(
        t: ParserRuleContext?,
        root: ParserRuleContext?,
        startIndex: Int,
        stopIndex: Int
    ) {
        if (t == null) return
        for (i in 0..<t.childCount) {
            val child: ParseTree = t.getChild(i)
            val range: Interval = child.sourceInterval
            if (child is ParserRuleContext && (range.b < startIndex || range.a > stopIndex)) {
                if (org.antlr.v4.runtime.tree.Trees.isAncestorOf(
                        child,
                        root
                    )
                ) { // replace only if subtree doesn't have displayed root
                    val abbrev: CommonToken = CommonToken(Token.INVALID_TYPE, "...")
                    t.children.set(i, TerminalNodeImpl(abbrev))
                }
            }
        }
    }

    /** Return first node satisfying the pred
     *
     * @since 4.5.1
     */
    fun findNodeSuchThat(t: Tree?, pred: Predicate<Tree?>): Tree? {
        if (pred.test(t)) return t

        if (t == null) return null

        val n: Int = t.childCount
        for (i in 0..<n) {
            val u: Tree? = org.antlr.v4.runtime.tree.Trees.findNodeSuchThat(t.getChild(i), pred)
            if (u != null) return u
        }
        return null
    }
}
