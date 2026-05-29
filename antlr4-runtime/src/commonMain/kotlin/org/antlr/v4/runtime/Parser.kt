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
    override val grammarFileName: String
        get() = "unknown"


    open inner class TraceListener : ParseTreeListener {
        override fun enterEveryRule(ctx: ParserRuleContext?) {
            println(
                "enter   " + ruleNames?.get(ctx?.ruleIndex ?: 0) +
                        ", LT(1)=" + _input!!.LT(1)?.text
            )
        }
        override fun visitTerminal(node: TerminalNode?) {
            println(
                "consume " + node?.symbol + " rule " +
                        ruleNames?.get(_ctx!!.ruleIndex)
            )
        }
        override fun visitErrorNode(node: ErrorNode?) {}

        override fun exitEveryRule(ctx: ParserRuleContext?) {
            println(
                "exit    " + ruleNames?.get(ctx?.ruleIndex ?: 0) +
                        ", LT(1)=" + _input!!.LT(1)?.text ?: ""
            )
        }
    }

    open class TrimToSizeListener : ParseTreeListener {
        override fun enterEveryRule(ctx: ParserRuleContext?) {
        }
        override fun visitTerminal(node: TerminalNode?) {
        }
        override fun visitErrorNode(node: ErrorNode?) {}

        override fun exitEveryRule(ctx: ParserRuleContext?) {
            if (ctx?.children is ArrayList) {
                (ctx?.children as? ArrayList<*>)?.trimToSize()
            }
        }

        companion object {
            val INSTANCE: TrimToSizeListener = TrimToSizeListener()
        }
    }





    /** The input stream (set via constructor or setInputStream). */
    protected var _input: TokenStream? = null

    /**
     * Consume the current symbol and advance to the next.
     * This is the primary method for advancing the parser during matching.
     */
    open fun consume(): Token {
        val o: Token = currentToken
        if (o.type != IntStream.EOF) {
            _input!!.consume()
        }
        val hasListener: Boolean = _parseListeners != null && !_parseListeners!!.isEmpty()
        if (buildParseTree || hasListener) {
            if (_errHandler!!.inErrorRecoveryMode(this)) {
                val node: ErrorNode = _ctx!!.addErrorNode(createErrorNode(_ctx, o))!!
                if (_parseListeners != null) {
                    for (listener in _parseListeners!!) {
                        listener.visitErrorNode(node)
                    }
                }
            } else {
                val node: TerminalNode? = _ctx!!.addChild(TerminalNodeImpl(o))
                if (_parseListeners != null) {
                    for (listener in _parseListeners!!) {
                        listener.visitTerminal(node)
                    }
                }
            }
        }
        return o
    }

    /** Create error node during recovery. */
    open fun createErrorNode(ctx: ParserRuleContext?, t: Token): ErrorNode = ErrorNodeImpl(t)

    /** Serialized ATN for bypass-alts cache. */
    open override val serializedATN: String get() = throw UnsupportedOperationException("there is no serialized ATN")

    /** Trace flag. */
    protected var isTrace: Boolean = false

    /** Bypass alts ATN cache (lazy, unsynchronized). */
    private var bypassAltsAtnCache: ATN? = null

    /** Current input token. */
    val currentToken: Token
        get() = _input!!.LT(1)!!

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
    open fun reset() {
        if (this.inputStream != null) this.inputStream?.seek(0)
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
            t = _errHandler.recoverInline(this)!!
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
            t = _errHandler.recoverInline(this)!!
            if (this.buildParseTree && t.tokenIndex === -1) {
                // we must have conjured up a new token during single token insertion
                // if it's not the current symbol
                _ctx!!.addErrorNode(createErrorNode(_ctx, t))
            }
        }

        return t
    }

    val trimParseTree: Boolean
        get() = parseListeners.contains(Parser.TrimToSizeListener.INSTANCE)

    fun setTrimParseTree(trimParseTrees: Boolean) {
        if (trimParseTrees) {
            if (trimParseTree) return
            addParseListener(Parser.TrimToSizeListener.INSTANCE)
        } else {
            removeParseListener(Parser.TrimToSizeListener.INSTANCE)
        }
    }


    val parseListeners: List<ParseTreeListener>
        get() {
            val listeners = _parseListeners
            if (listeners == null) {
                return emptyList()
            }

            return listeners.toList()
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
            _parseListeners = ArrayList<ParseTreeListener>()
        }

        _parseListeners!!.add(listener)
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
            if (_parseListeners!!.remove(listener)) {
                if (_parseListeners!!.isEmpty()) {
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
        val listeners = _parseListeners ?: return
        for (i in listeners.size - 1 downTo 0) {
            val listener = listeners[i]
            _ctx!!.exitRule(listener)
            listener.exitEveryRule(_ctx)
        }
    }
    override val tokenFactory: TokenFactory<*>?
        get() = _input?.tokenSource?.tokenFactory

    /** Tell our token source and error strategy about a new way to create tokens.  */
    override fun setTokenFactory(factory: TokenFactory<*>?) {
        _input!!.tokenSource?.setTokenFactory(factory)
    }

    override fun setInputStream(input: IntStream?) {
        _input = input as? TokenStream
    }

    override val inputStream: IntStream?
        get() = _input

    /**
     * Notify error listeners of a syntax error.
     */
    fun notifyErrorListeners(msg: String) {
        notifyErrorListeners(currentToken, msg, null)
    }

    /**
     * Notify error listeners of a syntax error with a specific token and exception.
     */
    fun notifyErrorListeners(offendingToken: Token?, msg: String?, e: RecognitionException?) {
        numberOfSyntaxErrors++
        val line = offendingToken?.line ?: 0
        val charPositionInLine = offendingToken?.charPositionInLine ?: 0
        errorListenerDispatch.syntaxError(this, offendingToken, line, charPositionInLine, msg, e)
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
            val serializedAtn = serializedATN
            if (serializedAtn == null) {
                throw UnsupportedOperationException("The current parser does not support an ATN with bypass alternatives.")
            }

            if (bypassAltsAtnCache == null) {
                val deserializationOptions: ATNDeserializationOptions = ATNDeserializationOptions()
                deserializationOptions.setGenerateRuleBypassTransitions(true)
                bypassAltsAtnCache = ATNDeserializer(deserializationOptions).deserialize(serializedAtn.toCharArray())
            }
            return bypassAltsAtnCache
        }

    /** Get the precedence level for the top-most precedence rule.
     * @return The precedence level for the top-most precedence rule, or -1 if
     * the parser context is not nested within a precedence rule.
     */
    val precedence: Int
        get() {
            if ((_precedenceStack.size() == 0)) {
                return -1
            }
            return _precedenceStack.peek()
        }

    open fun enterRecursionRule(localctx: ParserRuleContext?, state: Int, ruleIndex: Int, precedence: Int) {
        _precedenceStack.push(precedence)
        this.state = state
        _ctx = localctx
        _ctx!!.start = _input!!.LT(1)
    }

    open fun unrollRecursionContexts(_parentctx: ParserRuleContext?) {
        _precedenceStack.pop()
        _ctx!!.stop = _input!!.LT(-1)
        val retctx = _ctx
        if (_parseListeners != null) {
            while (_ctx != _parentctx) {
                triggerExitRuleEvent()
                _ctx = _ctx!!.parent as ParserRuleContext
            }
        } else {
            _ctx = _parentctx
        }
        retctx!!.setParent(_parentctx)
        if (buildParseTree && _parentctx != null) {
            _parentctx.addChild(retctx)
        }
    }

    override fun precpred(localctx: RuleContext?, precedence: Int): Boolean {
        return precedence >= _precedenceStack.peek()
    }

    val tokenStream: TokenStream
        get() = _input!!
    fun getRuleInvocationStack(): List<String> = getRuleInvocationStack(_ctx)

    fun getRuleInvocationStack(p: RuleContext?): List<String> {
        val ruleNames = ruleNames ?: return emptyList()
        val stack = mutableListOf<String>()
        var ctx: RuleContext? = p
        while (ctx != null) {
            val ruleIndex = ctx.ruleIndex
            if (ruleIndex < 0) stack.add("n/a")
            else stack.add(ruleNames[ruleIndex])
            ctx = ctx.parent as? RuleContext
        }
        return stack
    }

    fun enterRule(localctx: ParserRuleContext, state: Int, ruleIndex: Int) {
        this.state = state
        _ctx = localctx
        _ctx!!.start = _input!!.LT(1)
        if (buildParseTree) addContextToParseTree()
        if (_parseListeners != null) triggerEnterRuleEvent()
    }

    fun exitRule() {
        if (isMatchedEOF) {
            _ctx!!.stop = _input!!.LT(1)
        } else {
            _ctx!!.stop = _input!!.LT(-1)
        }
        if (_parseListeners != null) triggerExitRuleEvent()
        state = _ctx!!.invokingState
        _ctx = _ctx!!.parent as ParserRuleContext?
    }

    fun pushNewRecursionContext(localctx: ParserRuleContext, state: Int, ruleIndex: Int) {
        val previous = _ctx
        previous!!.setParent(localctx)
        previous!!.invokingState = state
        _ctx = localctx
        _ctx!!.start = previous!!.start
        if (buildParseTree) {
            (previous!!.parent as ParserRuleContext).addChild(previous!!)
        }
    }

    fun getContext(): ParserRuleContext? = _ctx



    protected fun addContextToParseTree() {
        val parentCtx = _ctx?.parent as? ParserRuleContext ?: return
        parentCtx.addChild(_ctx!!)
    }

}