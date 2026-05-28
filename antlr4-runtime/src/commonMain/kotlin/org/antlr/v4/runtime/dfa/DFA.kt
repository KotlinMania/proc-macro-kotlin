/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.dfa

import org.antlr.v4.runtime.Vocabulary
import org.antlr.v4.runtime.VocabularyImpl
import org.antlr.v4.runtime.atn.ATNConfigSet
import org.antlr.v4.runtime.atn.DecisionState
import org.antlr.v4.runtime.atn.StarLoopEntryState

class DFA(
    atnStartState: DecisionState?,
    decision: Int,
) {
    val states: MutableMap<DFAState, DFAState> = HashMap()

    @kotlin.concurrent.Volatile
    var s0: DFAState? = null

    val decision: Int

    val atnStartState: DecisionState

    private val precedenceDfa: Boolean

    constructor(atnStartState: DecisionState) : this(atnStartState, 0)

    init {
        this.atnStartState = atnStartState
        this.decision = decision

        var precedenceDfa = false
        if (atnStartState is StarLoopEntryState) {
            if (atnStartState.isPrecedenceDecision) {
                precedenceDfa = true
                val precedenceState = DFAState(ATNConfigSet())
                precedenceState.edges = arrayOfNulls(0)
                precedenceState.isAcceptState = false
                precedenceState.requiresFullContext = false
                this.s0 = precedenceState
            }
        }

        this.precedenceDfa = precedenceDfa
    }

    fun isPrecedenceDfa(): Boolean = precedenceDfa

    fun getPrecedenceStartState(precedence: Int): DFAState? {
        check(isPrecedenceDfa()) { "Only precedence DFAs may contain a precedence start state." }
        if (precedence < 0 || precedence >= s0!!.edges.size) {
            return null
        }
        return s0!!.edges[precedence]
    }

    fun setPrecedenceStartState(
        precedence: Int,
        startState: DFAState?,
    ) {
        check(isPrecedenceDfa()) { "Only precedence DFAs may contain a precedence start state." }
        if (precedence < 0) return
        synchronized(s0!!) {
            if (precedence >= s0!!.edges.size) {
                s0!!.edges = s0!!.edges.copyOf(precedence + 1)
            }
            s0!!.edges[precedence] = startState
        }
    }

    @Deprecated("This method no longer performs any action.")
    fun setPrecedenceDfa(precedenceDfa: Boolean) {
        if (precedenceDfa != isPrecedenceDfa()) {
            throw UnsupportedOperationException("The precedenceDfa field cannot change after a DFA is constructed.")
        }
    }

    fun getStates(): List<DFAState> {
        val result = ArrayList<DFAState>(states.keys)
        result.sortBy { it.stateNumber }
        return result
    }

    override fun toString(): String = toString(VocabularyImpl.EMPTY_VOCABULARY)

    @Deprecated("Use toString(Vocabulary) instead.")
    fun toString(tokenNames: Array<String>?): String {
        if (s0 == null) return ""
        return DFASerializer(this, tokenNames).toString()
    }

    fun toString(vocabulary: Vocabulary): String {
        if (s0 == null) return ""
        return DFASerializer(this, vocabulary).toString()
    }

    fun toLexerString(): String {
        if (s0 == null) return ""
        return LexerDFASerializer(this).toString()
    }
}
