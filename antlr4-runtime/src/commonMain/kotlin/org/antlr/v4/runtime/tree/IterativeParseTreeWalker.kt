package org.antlr.v4.runtime.tree

import org.antlr.v4.runtime.misc.IntStack

class IterativeParseTreeWalker : ParseTreeWalker() {
    override fun walk(
        listener: ParseTreeListener,
        t: ParseTree?,
    ) {
        val nodeStack = ArrayDeque<ParseTree?>()
        val indexStack = IntStack()

        var currentNode: ParseTree? = t
        var currentIndex = 0

        while (currentNode != null) {
            if (currentNode is ErrorNode) {
                listener.visitErrorNode(currentNode)
            } else if (currentNode is TerminalNode) {
                listener.visitTerminal(currentNode)
            } else {
                val r: RuleNode = currentNode as RuleNode
                enterRule(listener, r)
            }

            if (currentNode.childCount > 0) {
                nodeStack.addLast(currentNode)
                indexStack.push(currentIndex)
                currentIndex = 0
                currentNode = currentNode.getChild(0)
                continue
            }

            do {
                if (currentNode is RuleNode) {
                    exitRule(listener, currentNode as RuleNode)
                }

                if (nodeStack.isEmpty()) {
                    currentNode = null
                    currentIndex = 0
                    break
                }

                currentNode = nodeStack.last()?.getChild(++currentIndex)
                if (currentNode != null) {
                    break
                }

                currentNode = nodeStack.removeLast()
                currentIndex = indexStack.pop()
            } while (currentNode != null)
        }
    }
}
