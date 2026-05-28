/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

import org.antlr.v4.runtime.atn.ATN
import org.antlr.v4.runtime.atn.ATNSimulator
import org.antlr.v4.runtime.atn.ParseInfo
import org.antlr.v4.runtime.misc.Utils

abstract class Recognizer<Symbol, ATNInterpreter : ATNSimulator> {
    private val _listeners: MutableList<ANTLRErrorListener> = mutableListOf(ConsoleErrorListener.INSTANCE)

    protected var _errHandler: ANTLRErrorStrategy = DefaultErrorStrategy()

    var errorHandler: ANTLRErrorStrategy
        get() = _errHandler
        set(handler) {
            _errHandler = handler
        }

    /**
     * Get the ATN interpreter used by the recognizer for prediction.
     */
    var interpreter: ATNInterpreter? = null

    /** Indicate that the recognizer has changed internal state that is
     * consistent with the ATN state passed in.
     */
    var state: Int = -1

    @Deprecated("Use vocabulary instead.")
    abstract val tokenNames: Array<String>?

    abstract val ruleNames: Array<String>?

    @Suppress("DEPRECATION")
    open val vocabulary: Vocabulary
        get() = VocabularyImpl.fromTokenNames(this.tokenNames)

    val tokenTypeMap: Map<String, Int>
        get() {
            val vocabulary: Vocabulary = this.vocabulary
            var result: Map<String, Int>? = Companion.tokenTypeMapCache[vocabulary]
            if (result == null) {
                val mutableResult = mutableMapOf<String, Int>()
                for (i in 0..this.atn.maxTokenType) {
                    val literalName: String? = vocabulary.getLiteralName(i)
                    if (literalName != null) {
                        mutableResult[literalName] = i
                    }
                    val symbolicName: String? = vocabulary.getSymbolicName(i)
                    if (symbolicName != null) {
                        mutableResult[symbolicName] = i
                    }
                }
                mutableResult["EOF"] = Token.EOF
                result = mutableResult.toMap()
                Companion.tokenTypeMapCache[vocabulary] = result!!
            }
            return result!!
        }

    val ruleIndexMap: Map<String, Int>
        get() {
            val ruleNames = this.ruleNames
            if (ruleNames == null) {
                throw UnsupportedOperationException("The current recognizer does not provide a list of rule names.")
            }
            var result: Map<String, Int>? = Companion.ruleIndexMapCache[ruleNames]
            if (result == null) {
                result = ruleNames.mapIndexed { i, name -> name to i }.toMap()
                Companion.ruleIndexMapCache[ruleNames] = result!!
            }
            return result!!
        }

    fun getTokenType(tokenName: String): Int {
        val ttype: Int? = this.tokenTypeMap[tokenName]
        if (ttype != null) return ttype
        return Token.INVALID_TYPE
    }

    fun getRuleIndex(ruleName: String): Int {
        val ruleIndex: Int? = this.ruleIndexMap[ruleName]
        if (ruleIndex != null) return ruleIndex
        return -1
    }

    val serializedATN: String
        get() = throw UnsupportedOperationException("there is no serialized ATN")

    abstract val grammarFileName: String

    abstract val atn: ATN

    val parseInfo: ParseInfo?
        get() = null

    fun getErrorHeader(e: RecognitionException): String {
        val line: Int = e.offendingToken!!.line
        val charPositionInLine: Int = e.offendingToken!!.charPositionInLine
        return "line $line:$charPositionInLine"
    }

    @Deprecated("Use DefaultErrorStrategy.getTokenErrorDisplay instead.")
    fun getTokenErrorDisplay(t: Token?): String {
        if (t == null) return "<no token>"
        var s: String = t.text ?: if (t.type == Token.EOF) "<EOF>" else "<${t.type}>"
        s = s.replace("\n", "\\n")
        s = s.replace("\r", "\\r")
        s = s.replace("\t", "\\t")
        return "'$s'"
    }

    fun addErrorListener(listener: ANTLRErrorListener) {
        requireNotNull(listener) { "listener cannot be null." }
        _listeners.add(listener)
    }

    fun removeErrorListener(listener: ANTLRErrorListener) {
        _listeners.remove(listener)
    }

    fun removeErrorListeners() {
        _listeners.clear()
    }

    val errorListeners: List<ANTLRErrorListener>
        get() = _listeners.toList()

    val errorListenerDispatch: ANTLRErrorListener
        get() = ProxyErrorListener(this.errorListeners)

    fun sempred(_localctx: RuleContext?, ruleIndex: Int, actionIndex: Int): Boolean = true

    fun precpred(localctx: RuleContext?, precedence: Int): Boolean = true

    fun action(_localctx: RuleContext?, ruleIndex: Int, actionIndex: Int) {}

    abstract val inputStream: IntStream?

    abstract fun setInputStream(input: IntStream?)

    abstract val tokenFactory: TokenFactory<*>?

    abstract fun setTokenFactory(input: TokenFactory<*>?)

    companion object {
        val EOF: Int = -1
        private val tokenTypeMapCache: MutableMap<Vocabulary, Map<String, Int>> = HashMap()
        private val ruleIndexMapCache: MutableMap<Array<String>, Map<String, Int>> = HashMap()
    }
}
