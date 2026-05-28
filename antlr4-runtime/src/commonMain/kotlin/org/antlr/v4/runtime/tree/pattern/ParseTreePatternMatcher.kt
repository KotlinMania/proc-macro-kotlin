package org.antlr.v4.runtime.tree.pattern

import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.BailErrorStrategy
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.ListTokenSource
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.ParserInterpreter
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.misc.MultiMap
import org.antlr.v4.runtime.misc.ParseCancellationException
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.RuleNode
import org.antlr.v4.runtime.tree.TerminalNode

class ParseTreePatternMatcher(lexer: Lexer, parser: Parser) {
    class CannotInvokeStartRule(e: Throwable?) : RuntimeException(e)
    class StartRuleDoesNotConsumeFullPattern : RuntimeException()

    private val lexer: Lexer
    private val parser: Parser

    protected var start: String = "<"
    protected var stop: String = ">"
    protected var escape: String = "\\"

    init {
        this.lexer = lexer
        this.parser = parser
    }

    fun setDelimiters(start: String, stop: String, escapeLeft: String) {
        require(start.isNotEmpty()) { "start cannot be null or empty" }
        require(stop.isNotEmpty()) { "stop cannot be null or empty" }
        this.start = start
        this.stop = stop
        this.escape = escapeLeft
    }

    fun matches(tree: ParseTree, pattern: String, patternRuleIndex: Int): Boolean {
        val p = compile(pattern, patternRuleIndex)
        return matches(tree, p)
    }

    fun matches(tree: ParseTree, pattern: ParseTreePattern): Boolean {
        val labels = MultiMap<String, ParseTree>()
        val mismatchedNode = matchImpl(tree, pattern.getPatternTree()!!, labels)
        return mismatchedNode == null
    }

    fun match(tree: ParseTree, pattern: String, patternRuleIndex: Int): ParseTreeMatch {
        val p = compile(pattern, patternRuleIndex)
        return match(tree, p)
    }

    fun match(tree: ParseTree, pattern: ParseTreePattern): ParseTreeMatch {
        val labels = MultiMap<String, ParseTree>()
        val mismatchedNode = matchImpl(tree, pattern.getPatternTree()!!, labels)
        return ParseTreeMatch(tree, pattern, labels, mismatchedNode)
    }

    fun compile(pattern: String, patternRuleIndex: Int): ParseTreePattern {
        val tokenList = tokenize(pattern)
        val tokenSrc = ListTokenSource(tokenList)
        val tokens = CommonTokenStream(tokenSrc)

        val parserInterp = ParserInterpreter(
            parser.grammarFileName,
            parser.vocabulary,
            listOf(*(parser.ruleNames ?: emptyArray())),
            parser.aTNWithBypassAlts!!,
            tokens
        )

        var tree: ParseTree? = null
        try {
            parserInterp.errorHandler = BailErrorStrategy()
            tree = parserInterp.parse(patternRuleIndex)
        } catch (re: RecognitionException) {
            throw re
        } catch (e: Exception) {
            if (e is ParseCancellationException) {
                throw (e.cause as RecognitionException)
            }
            throw CannotInvokeStartRule(e)
        }

        if (tokens.LA(1) !== Token.EOF) {
            throw StartRuleDoesNotConsumeFullPattern()
        }

        return ParseTreePattern(this, pattern, patternRuleIndex, tree)
    }

    fun getLexer(): Lexer = lexer

    fun getParser(): Parser = parser

    protected fun matchImpl(
        tree: ParseTree,
        patternTree: ParseTree,
        labels: MultiMap<String, ParseTree>
    ): ParseTree? {
        requireNotNull(tree) { "tree cannot be null" }
        requireNotNull(patternTree) { "patternTree cannot be null" }

        if (tree is TerminalNode && patternTree is TerminalNode) {
            val t1 = tree
            val t2 = patternTree
            var mismatchedNode: ParseTree? = null

            if (t1.symbol?.type == t2.symbol?.type) {
                val t2Symbol = t2.symbol
                if (t2Symbol is TokenTagToken) {
                    labels.map(t2Symbol.tokenName ?: "", tree)
                    if (t2Symbol.label != null) {
                        labels.map(t2Symbol.label, tree)
                    }
                } else if (t1.text != t2.text) {
                    if (mismatchedNode == null) {
                        mismatchedNode = t1
                    }
                }
            } else {
                if (mismatchedNode == null) {
                    mismatchedNode = t1
                }
            }
            return mismatchedNode
        }

        if (tree is ParserRuleContext && patternTree is ParserRuleContext) {
            val r1 = tree
            val r2 = patternTree
            var mismatchedNode: ParseTree? = null

            val ruleTagToken = getRuleTagToken(r2)
            if (ruleTagToken != null) {
                if (r1.ruleContext.ruleIndex == r2.ruleContext.ruleIndex) {
                    labels.map(ruleTagToken.ruleName, tree)
                    if (ruleTagToken.label != null) {
                        labels.map(ruleTagToken.label, tree)
                    }
                } else {
                    if (mismatchedNode == null) {
                        mismatchedNode = r1
                    }
                }
                return mismatchedNode
            }

            if (r1.childCount != r2.childCount) {
                if (mismatchedNode == null) {
                    mismatchedNode = r1
                }
                return mismatchedNode
            }

            val n = r1.childCount
            for (i in 0..<n) {
                val child1 = r1.getChild(i) ?: continue
                val child2 = r2.getChild(i) ?: continue
                val childMatch = matchImpl(child1, child2, labels)
                if (childMatch != null) {
                    return childMatch
                }
            }
            return mismatchedNode
        }

        return tree
    }

    protected fun getRuleTagToken(t: ParseTree?): RuleTagToken? {
        if (t is RuleNode) {
            val r = t
            if (r.childCount == 1 && r.getChild(0) is TerminalNode) {
                val c = r.getChild(0) as TerminalNode
                val cSymbol = c.symbol
                if (cSymbol is RuleTagToken) {
                    return cSymbol
                }
            }
        }
        return null
    }

    fun tokenize(pattern: String): List<Token> {
        val chunks = split(pattern)
        val tokens = mutableListOf<Token>()

        for (chunk in chunks) {
            if (chunk is TagChunk) {
                val tagChunk = chunk
                if (tagChunk.getTag()[0].isUpperCase()) {
                    val ttype = parser.getTokenType(tagChunk.getTag())
                    require(ttype !== Token.INVALID_TYPE) { "Unknown token ${tagChunk.getTag()} in pattern: $pattern" }
                    val t = TokenTagToken(tagChunk.getTag(), ttype, tagChunk.getLabel())
                    tokens.add(t)
                } else if (tagChunk.getTag()[0].isLowerCase()) {
                    val ruleIndex = parser.getRuleIndex(tagChunk.getTag())
                    require(ruleIndex != -1) { "Unknown rule ${tagChunk.getTag()} in pattern: $pattern" }
                    val ruleImaginaryTokenType = parser.aTNWithBypassAlts!!.ruleToTokenType[ruleIndex]
                    tokens.add(RuleTagToken(tagChunk.getTag(), ruleImaginaryTokenType, tagChunk.getLabel()))
                } else {
                    throw IllegalArgumentException("invalid tag: ${tagChunk.getTag()} in pattern: $pattern")
                }
            } else {
                val textChunk = chunk as TextChunk
                val input = ANTLRInputStream(textChunk.getText())
                lexer.setInputStream(input)
                var t = lexer.nextToken()
                while (t.type !== Token.EOF) {
                    tokens.add(t)
                    t = lexer.nextToken()
                }
            }
        }

        return tokens
    }

    fun split(pattern: String): List<Chunk> {
        var p = 0
        val n = pattern.length
        val chunks = mutableListOf<Chunk>()
        val starts = mutableListOf<Int>()
        val stops = mutableListOf<Int>()

        while (p < n) {
            when {
                p == pattern.indexOf(escape + start, p) -> p += escape.length + start.length
                p == pattern.indexOf(escape + stop, p) -> p += escape.length + stop.length
                p == pattern.indexOf(start, p) -> { starts.add(p); p += start.length }
                p == pattern.indexOf(stop, p) -> { stops.add(p); p += stop.length }
                else -> p++
            }
        }

        require(starts.size <= stops.size) { "unterminated tag in pattern: $pattern" }
        require(starts.size >= stops.size) { "missing start tag in pattern: $pattern" }

        val ntags = starts.size

        for (i in 0..<ntags) {
            require(starts[i] < stops[i]) { "tag delimiters out of order in pattern: $pattern" }
        }

        if (ntags == 0) {
            val text = pattern.substring(0, n)
            chunks.add(TextChunk(text))
        }

        if (ntags > 0 && starts[0] > 0) {
            val text = pattern.substring(0, starts[0])
            chunks.add(TextChunk(text))
        }

        for (i in 0..<ntags) {
            val tag = pattern.substring(starts[i] + start.length, stops[i])
            var ruleOrToken = tag
            var label: String? = null
            val colon = tag.indexOf(':')
            if (colon >= 0) {
                label = tag.substring(0, colon)
                ruleOrToken = tag.substring(colon + 1, tag.length)
            }
            chunks.add(TagChunk(label, ruleOrToken))
            if (i + 1 < ntags) {
                val text = pattern.substring(stops[i] + stop.length, starts[i + 1])
                chunks.add(TextChunk(text))
            }
        }

        if (ntags > 0) {
            val afterLastTag = stops[ntags - 1] + stop.length
            if (afterLastTag < n) {
                val text = pattern.substring(afterLastTag, n)
                chunks.add(TextChunk(text))
            }
        }

        for (i in 0..<chunks.size) {
            val c = chunks[i]
            if (c is TextChunk) {
                val tc = c
                val unescaped = tc.getText().replace(escape, "")
                if (unescaped.length < tc.getText().length) {
                    chunks[i] = TextChunk(unescaped)
                }
            }
        }

        return chunks
    }
}
