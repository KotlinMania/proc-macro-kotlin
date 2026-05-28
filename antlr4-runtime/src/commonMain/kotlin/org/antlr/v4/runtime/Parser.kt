/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

import org.antlr.v4.runtime.atn.ATN
import org.antlr.v4.runtime.atn.ATNDeserializationOptions
import org.antlr.v4.runtime.atn.ATNDeserializer
import org.antlr.v4.runtime.atn.ATNSimulator
import org.antlr.v4.runtime.atn.ATNState
import org.antlr.v4.runtime.atn.ParseInfo
import org.antlr.v4.runtime.atn.ParserATNSimulator
import org.antlr.v4.runtime.atn.PredictionMode
import org.antlr.v4.runtime.atn.ProfilingATNSimulator
import org.antlr.v4.runtime.atn.RuleTransition
import org.antlr.v4.runtime.dfa.DFA
import org.antlr.v4.runtime.misc.IntStack
import org.antlr.v4.runtime.misc.IntervalSet
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ErrorNodeImpl
import org.antlr.v4.runtime.tree.ParseTreeListener
import org.antlr.v4.runtime.tree.TerminalNode
import org.antlr.v4.runtime.tree.TerminalNodeImpl

/** This is all the parsing support code essentially; most of it is error recovery stuff.  */
abstract class Parser(input: TokenStream?) : Recognizer<Token, ParserATNSimulator>() {
    inner class TraceListener : ParseTreeListener {
        override fun enterEveryRule(ctx: ParserRuleContext) {
            println(
                "enter   " + ruleNames[ctx.ruleIndex] +
                        ", LT(1)=" + _input!!.LT(1).text
            )
        }
        fun visitTerminal(node: TerminalNode) {
            println(
                "consume " + node.symbol + " rule " +
                        ruleNames[_ctx!!.ruleIndex]
            )
        }
        fun visitErrorNode(node: ErrorNode?) {
        }
        override fun exitEveryRule(ctx: ParserRuleContext) {
            println(
                "exit    " + ruleNames[ctx.ruleIndex] +
                        ", LT(1)=" + _input!!.LT(1).text
            )
        }
    }

    class TrimToSizeListener : ParseTreeListener {
        fun enterEveryRule(ctx: ParserRuleContext?) {
        }
        fun visitTerminal(node: TerminalNode?) {
        }
        fun visitErrorNode(node: ErrorNode?) {
        }
        override fun exitEveryRule(ctx: ParserRuleContext) {
            if (ctx.children is ArrayList) {
                (ctx.children as ArrayList<*>).trimToSize()
            }
        }

        companion object {
            val INSTANCE: TrimToSizeListener = org.antlr.v4.runtime.Parser.TrimToSizeListener()
        }
    }





    protected val _precedenceStack: IntStack

    init {
        _precedenceStack = IntStack()
        _precedenceStack.push(0)
    }

    /**
     * The [ParserRuleContext] object for the currently executing rule.
     * This is always non-null during the parsing process.
     */
    internal var _ctx: ParserRuleContext? = null

    /**
     * Gets whether or not a complete parse tree will be constructed while
     * parsing. This property is `true` for a newly constructed parser.
     *
     * @return `true` if a complete parse tree will be constructed while
     * parsing, otherwise `false`
     */
    /**
     * Track the [ParserRuleContext] objects during the parse and hook
     * them up using the [ParserRuleContext.children] list so that it
     * forms a parse tree. The [ParserRuleContext] returned from the start
     * rule represents the root of the parse tree.
     *
     *
     * Note that if we are not building parse trees, rule contexts only point
     * upwards. When a rule exits, it returns the context but that gets garbage
     * collected if nobody holds a reference. It points upwards but nobody
     * points at it.
     *
     *
     * When we build parse trees, we are adding all of these contexts to
     * [ParserRuleContext.children] list. Contexts are then not candidates
     * for garbage collection.
     */
    /**
     * Specifies whether or not the parser should construct a parse tree during
     * the parsing process. The default value is `true`.
     *
     * @see .getBuildParseTree
     *
     * @see .setBuildParseTree
     */
    var buildParseTree: Boolean = true


    /**
     * When [.setTrace]`(true)` is called, a reference to the
     * [TraceListener] is stored here so it can be easily removed in a
     * later call to [.setTrace]`(false)`. The listener itself is
     * implemented as a parser listener so this field is not directly used by
     * other parser methods.
     */
    private var _tracer: TraceListener? = null

    /**
     * The list of [ParseTreeListener] listeners registered to receive
     * events during the parse.
     *
     * @see .addParseListener
     */
    protected var _parseListeners: MutableList<ParseTreeListener>? = null

    /**
     * Gets the number of syntax errors reported during parsing. This value is
     * incremented each time [.notifyErrorListeners] is called.
     *
     * @see .notifyErrorListeners
     */
    /**
     * The number of syntax errors reported during parsing. This value is
     * incremented each time [.notifyErrorListeners] is called.
     */
    var numberOfSyntaxErrors: Int = 0
        protected set

    /** Indicates parser has match()ed EOF token. See [.exitRule].  */
    var isMatchedEOF: Boolean = false
        protected set

    init {
        setInputStream(input)
    }

    /** reset the parser's state  */
    override fun reset() {
        if (this.inputStream != null) this.inputStream.seek(0)
        _errHandler.reset(this)
        _ctx = null
        this.numberOfSyntaxErrors = 0
        this.isMatchedEOF = false
        this.isTrace = false
        _precedenceStack.clear()
        _precedenceStack.push(0)
        val interp: ATNSimulator? = this.interpreter
        if (interp != null) {
            interp!!.reset()
        }
    }

    /**
     * Match current input symbol against `ttype`. If the symbol type
     * matches, [ANTLRErrorStrategy.reportMatch] and [.consume] are
     * called to complete the match process.
     *
     *
     * If the symbol type does not match,
     * [ANTLRErrorStrategy.recoverInline] is called on the current error
     * strategy to attempt recovery. If [.getBuildParseTree] is
     * `true` and the token index of the symbol returned by
     * [ANTLRErrorStrategy.recoverInline] is -1, the symbol is added to
     * the parse tree by calling [.createErrorNode] then
     * [ParserRuleContext.addErrorNode].
     *
     * @param ttype the token type to match
     * @return the matched symbol
     * @throws RecognitionException if the current input symbol did not match
     * `ttype` and the error strategy could not recover from the
     * mismatched symbol
     */
    @kotlin.Throws(RecognitionException::class)
    fun match(ttype: Int): Token {
        var t: Token = this.currentToken
        if (t.type === ttype) {
            if (ttype == Token.EOF) {
                this.isMatchedEOF = true
            }
            _errHandler.reportMatch(this)
            consume()
        } else {
            t = _errHandler.recoverInline(this)
            if (this.buildParseTree && t.tokenIndex === -1) {
                // we must have conjured up a new token during single token insertion
                // if it's not the current symbol
                _ctx!!.addErrorNode(createErrorNode(_ctx, t))
            }
        }
        return t
    }

    /**
     * Match current input symbol as a wildcard. If the symbol type matches
     * (i.e. has a value greater than 0), [ANTLRErrorStrategy.reportMatch]
     * and [.consume] are called to complete the match process.
     *
     *
     * If the symbol type does not match,
     * [ANTLRErrorStrategy.recoverInline] is called on the current error
     * strategy to attempt recovery. If [.getBuildParseTree] is
     * `true` and the token index of the symbol returned by
     * [ANTLRErrorStrategy.recoverInline] is -1, the symbol is added to
     * the parse tree by calling [createErrorNode]. then
     * [ParserRuleContext.addErrorNode]
     *
     * @return the matched symbol
     * @throws RecognitionException if the current input symbol did not match
     * a wildcard and the error strategy could not recover from the mismatched
     * symbol
     */
    @kotlin.Throws(RecognitionException::class)
    fun matchWildcard(): Token {
        var t: Token = this.currentToken
        if (t.type > 0) {
            _errHandler.reportMatch(this)
            consume()
        } else {
            t = _errHandler.recoverInline(this)
            if (this.buildParseTree && t.tokenIndex === -1) {
                // we must have conjured up a new token during single token insertion
                // if it's not the current symbol
                _ctx!!.addErrorNode(createErrorNode(_ctx, t))
            }
        }

        return t
    }

    var trimParseTree: Boolean
        /**
         * @return `true` if the [ParserRuleContext.children] list is trimmed
         * using the default [TrimToSizeListener] during the parse process.
         */
        get() = this.parseListeners!!.contains(org.antlr.v4.runtime.Parser.TrimToSizeListener.Companion.INSTANCE)
        /**
         * Trim the internal lists of the parse tree during parsing to conserve memory.
         * This property is set to `false` by default for a newly constructed parser.
         *
         * @param trimParseTrees `true` to trim the capacity of the [ParserRuleContext.children]
         * list to its size after a rule is parsed.
         */
        set(trimParseTrees) {
            if (trimParseTrees) {
                if (field) return
                addParseListener(org.antlr.v4.runtime.Parser.TrimToSizeListener.Companion.INSTANCE)
            } else {
                removeParseListener(org.antlr.v4.runtime.Parser.TrimToSizeListener.Companion.INSTANCE)
            }
        }


    val parseListeners: List<ParseTreeListener>?
        get() {
            val listeners: List<ParseTreeListener?>? = _parseListeners
            if (listeners == null) {
                return emptyList()
            }

            return listeners
        }

    /**
     * Registers `listener` to receive events during the parsing process.
     *
     *
     * To support output-preserving grammar transformations (including but not
     * limited to left-recursion removal, automated left-factoring, and
     * optimized code generation), calls to listener methods during the parse
     * may differ substantially from calls made by
     * [ParseTreeWalker.DEFAULT] used after the parse is complete. In
     * particular, rule entry and exit events may occur in a different order
     * during the parse than after the parser. In addition, calls to certain
     * rule entry methods may be omitted.
     *
     *
     * With the following specific exceptions, calls to listener events are
     * *deterministic*, i.e. for identical input the calls to listener
     * methods will be the same.
     *
     *
     *  * Alterations to the grammar used to generate code may change the
     * behavior of the listener calls.
     *  * Alterations to the command line options passed to ANTLR 4 when
     * generating the parser may change the behavior of the listener calls.
     *  * Changing the version of the ANTLR Tool used to generate the parser
     * may change the behavior of the listener calls.
     *
     *
     * @param listener the listener to add
     *
     * @throws NullPointerException if `` listener is `null`
     */
    fun addParseListener(listener: ParseTreeListener) {
        if (listener == null) {
            throw NullPointerException("listener")
        }

        if (_parseListeners == null) {
            _parseListeners = ArrayList<ParseTreeListener?>()
        }

        this._parseListeners.add(listener)
    }

    /**
     * Remove `listener` from the list of parse listeners.
     *
     *
     * If `listener` is `null` or has not been added as a parse
     * listener, this method does nothing.
     *
     * @see .addParseListener
     *
     *
     * @param listener the listener to remove
     */
    fun removeParseListener(listener: ParseTreeListener?) {
        if (_parseListeners != null) {
            if (_parseListeners.remove(listener)) {
                if (_parseListeners!!.isEmpty) {
                    _parseListeners = null
                }
            }
        }
    }

    /**
     * Remove all parse listeners.
     *
     * @see .addParseListener
     */
    fun removeParseListeners() {
        _parseListeners = null
    }

    /**
     * Notify any parse listeners of an enter rule event.
     *
     * @see .addParseListener
     */
    protected fun triggerEnterRuleEvent() {
        for (listener in _parseListeners!!) {
            listener.enterEveryRule(_ctx)
            _ctx!!.enterRule(listener)
        }
    }

    /**
     * Notify any parse listeners of an exit rule event.
     *
     * @see .addParseListener
     */
    protected fun triggerExitRuleEvent() {
        // reverse order walk of listeners
        for (i in _parseListeners.size - 1 downTo 0) {
            val listener: ParseTreeListener = _parseListeners!!.get(i)
            _ctx!!.exitRule(listener)
            listener.exitEveryRule(_ctx)
        }
    }
    val tokenFactory: TokenFactory<*>
        get() = _input!!.tokenSource.tokenFactory

    /** Tell our token source and error strategy about a new way to create tokens.  */
    fun setTokenFactory(factory: TokenFactory<*>?) {
        _input!!.tokenSource.setTokenFactory(factory)
    }

    /**
     * The ATN with bypass alternatives is expensive to create so we create it
     * lazily.
     *
     * @throws UnsupportedOperationException if the current parser does not
     * implement the [getSerializedATN] method.
     */
    val aTNWithBypassAlts: ATN?
        get() {
            val serializedAtn: String = getSerializedATN()
            if (serializedAtn == null) {
                throw UnsupportedOperationException("The current parser does not support an ATN with bypass alternatives.")
            }

            synchronized(this) {
                if (bypassAltsAtnCache != null) {
                    return bypassAltsAtnCache
                }
                val deserializationOptions: ATNDeserializationOptions = ATNDeserializationOptions()
                deserializationOptions.setGenerateRuleBypassTransitions(true)
                bypassAltsAtnCache = ATNDeserializer(deserializationOptions).deserialize(serializedAtn.toCharArray())
                return bypassAltsAtnCache
            }
        }
}
