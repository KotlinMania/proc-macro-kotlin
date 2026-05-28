/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.tree

import org.antlr.v4.runtime.ParserRuleContext

open class ParseTreeWalker {
    /**
     * Performs a walk on the given parse tree starting at the root and going down recursively
     * with depth-first search. On each node, [enterRule] is called before
     * recursively walking down into child nodes, then
     * [exitRule] is called after the recursive call to wind up.
     * @param listener The listener used by the walker to process grammar rules
     * @param t The parse tree to be walked on
     */
    open fun walk(listener: ParseTreeListener, t: ParseTree?) {
        if (t is ErrorNode) {
            listener.visitErrorNode(t as ErrorNode?)
            return
        } else if (t is TerminalNode) {
            listener.visitTerminal(t as TerminalNode?)
            return
        }
        val r: RuleNode = t as RuleNode
        enterRule(listener, r)
        val n: Int = r.childCount
        for (i in 0..<n) {
            walk(listener, r.getChild(i))
        }
        exitRule(listener, r)
    }

    /**
     * Enters a grammar rule by first triggering the generic event [ParseTreeListener.enterEveryRule]
     * then by triggering the event specific to the given parse tree node
     * @param listener The listener responding to the trigger events
     * @param r The grammar rule containing the rule context
     */
    protected fun enterRule(listener: ParseTreeListener, r: RuleNode) {
        val ctx: ParserRuleContext = r.ruleContext as ParserRuleContext
        listener.enterEveryRule(ctx)
        ctx.enterRule(listener)
    }


    /**
     * Exits a grammar rule by first triggering the event specific to the given parse tree node
     * then by triggering the generic event [ParseTreeListener.exitEveryRule]
     * @param listener The listener responding to the trigger events
     * @param r The grammar rule containing the rule context
     */
    protected fun exitRule(listener: ParseTreeListener, r: RuleNode) {
        val ctx: ParserRuleContext = r.ruleContext as ParserRuleContext
        ctx.exitRule(listener)
        listener.exitEveryRule(ctx)
    }

    companion object {
        val DEFAULT: ParseTreeWalker = org.antlr.v4.runtime.tree.ParseTreeWalker()
    }
}
