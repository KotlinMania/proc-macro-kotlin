package org.antlr.v4.runtime.tree.xpath

import org.antlr.v4.runtime.tree.ParseTree

abstract class XPathElement(var nodeName: String) {
    var invert: Boolean = false

    abstract fun evaluate(t: ParseTree): Collection<ParseTree>

    override fun toString(): String {
        val inv = if (invert) "!" else ""
        return "${this::class.simpleName}[$inv$nodeName]"
    }
}
