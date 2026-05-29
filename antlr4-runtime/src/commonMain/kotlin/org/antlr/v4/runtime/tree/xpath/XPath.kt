package org.antlr.v4.runtime.tree.xpath

import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.LexerNoViableAltException
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree

open class XPath(
    protected var parser: Parser,
    path: String,
) {
    protected var elements: Array<XPathElement>

    companion object {
        const val WILDCARD: String = "*"
        const val NOT: String = "!"
        const val TOKEN_EXT: String = "//"

        fun findAll(
            tree: ParseTree?,
            xpath: String,
            parser: Parser,
        ): Collection<ParseTree?> {
            val p = XPath(parser, xpath)
            return p.evaluate(tree!!)
        }
    }

    init {
        elements = split(path)
    }

    fun split(path: String): Array<XPathElement> {
        val `in` = ANTLRInputStream(path)
        val lexer: XPathLexer =
            object : XPathLexer(`in`) {
                override fun recover(e: LexerNoViableAltException?) {
                    if (e != null) throw e
                }
            }
        lexer.removeErrorListeners()
        lexer.addErrorListener(XPathLexerErrorListener)
        val tokenStream = CommonTokenStream(lexer)
        try {
            tokenStream.fill()
        } catch (e: LexerNoViableAltException) {
            val pos = lexer.charPositionInLine
            val msg = "Invalid tokens or characters at index $pos in path '$path'"
            throw IllegalArgumentException(msg, e)
        }

        val tokens = tokenStream.allTokens()
        val elements = mutableListOf<XPathElement>()
        val n = tokens.size
        var i = 0
        loop@ while (i < n) {
            val el = tokens[i]
            when (el?.type) {
                XPathLexer.ROOT, XPathLexer.ANYWHERE -> {
                    val anywhere = el.type == XPathLexer.ANYWHERE
                    i++
                    var next = tokens[i]
                    val invert = next?.type == XPathLexer.BANG
                    if (invert) {
                        i++
                        next = tokens[i]
                    }
                    val pathElement = getXPathElement(next, anywhere)
                    pathElement.invert = invert
                    elements.add(pathElement)
                    i++
                }
                XPathLexer.TOKEN_REF, XPathLexer.RULE_REF, XPathLexer.WILDCARD -> {
                    elements.add(getXPathElement(el, false))
                    i++
                }
                Token.EOF -> {
                    break@loop
                }
                else -> {
                    throw IllegalArgumentException("Unknown path element $el")
                }
            }
        }
        return elements.toTypedArray()
    }

    protected fun getXPathElement(
        wordToken: Token?,
        anywhere: Boolean,
    ): XPathElement {
        val token = wordToken ?: throw IllegalArgumentException("Null token")
        require(token.type != Token.EOF) { "Missing path element at end of path" }
        val word = token.text ?: throw IllegalArgumentException("Null token text")
        val ttype = parser.getTokenType(word)
        val ruleIndex = parser.getRuleIndex(word)
        return when (token.type) {
            XPathLexer.WILDCARD -> if (anywhere) XPathWildcardAnywhereElement() else XPathWildcardElement()
            XPathLexer.TOKEN_REF, XPathLexer.STRING -> {
                if (ttype == Token.INVALID_TYPE) {
                    throw IllegalArgumentException(
                        "$word at index ${token.startIndex} isn't a valid token name",
                    )
                }
                if (anywhere) XPathTokenAnywhereElement(word, ttype) else XPathTokenElement(word, ttype)
            }
            else -> {
                if (ruleIndex == -1) {
                    throw IllegalArgumentException(
                        "$word at index ${token.startIndex} isn't a valid rule name",
                    )
                }
                if (anywhere) XPathRuleAnywhereElement(word, ruleIndex) else XPathRuleElement(word, ruleIndex)
            }
        }
    }

    fun evaluate(t: ParseTree?): Collection<ParseTree?> {
        val dummyRoot = ParserRuleContext()
        dummyRoot.children = mutableListOf(t!!)
        var work: MutableCollection<ParseTree?> = mutableListOf(dummyRoot)
        var i = 0
        while (i < elements.size) {
            val next: MutableCollection<ParseTree?> = linkedSetOf()
            for (node in work) {
                if (node != null && node.childCount > 0) {
                    val matching = elements[i].evaluate(node)
                    next.addAll(matching)
                }
            }
            i++
            work = next
        }
        return work
    }
}
