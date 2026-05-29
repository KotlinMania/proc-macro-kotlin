/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.misc.IntervalSet

class ATN(
    val grammarType: ATNType,
    val maxTokenType: Int,
) {
    val states: MutableList<ATNState> = ArrayList()

    val decisionToState: MutableList<DecisionState> = ArrayList()

    var ruleToStartState: Array<RuleStartState?> = arrayOf()
    var ruleToStopState: Array<RuleStopState?> = arrayOf()

    val modeNameToStartState: MutableMap<String, TokensStartState> = LinkedHashMap()

    var ruleToTokenType: IntArray = IntArray(0)
    var lexerActions: Array<LexerAction?> = arrayOf()

    val modeToStartState: MutableList<TokensStartState> = ArrayList()

    fun nextTokens(
        s: ATNState,
        ctx: RuleContext?,
    ): IntervalSet = LL1Analyzer(this).LOOK(s, ctx)!!

    fun nextTokens(s: ATNState): IntervalSet {
        if (s.nextTokenWithinRule != null) return s.nextTokenWithinRule!!
        s.nextTokenWithinRule = nextTokens(s, null)
        s.nextTokenWithinRule!!.makeReadonly()
        return s.nextTokenWithinRule!!
    }

    fun addState(state: ATNState?) {
        if (state != null) {
            state.atn = this
            state.stateNumber = states.size
        }
        states.add(
            state ?: object : ATNState() {
                override val stateType: Int = INVALID_TYPE
            },
        )
    }

    fun removeState(state: ATNState) {
        // Replace with invalid-state sentinel instead of null
        states[state.stateNumber] =
            object : ATNState() {
                override val stateType: Int = INVALID_TYPE
            }
    }

    fun defineDecisionState(s: DecisionState): Int {
        decisionToState.add(s)
        s.decision = decisionToState.size - 1
        return s.decision
    }

    fun getDecisionState(decision: Int): DecisionState? {
        if (decisionToState.isNotEmpty() && decision in decisionToState.indices) {
            return decisionToState[decision]
        }
        return null
    }

    val numberOfDecisions: Int
        get() = decisionToState.size

    fun getExpectedTokens(
        stateNumber: Int,
        context: RuleContext?,
    ): IntervalSet {
        require(stateNumber in 0 until states.size) { "Invalid state number." }

        var ctx = context
        val s = states[stateNumber]
        var following = nextTokens(s)
        if (!following.contains(Token.EPSILON)) return following

        val expected = IntervalSet()
        expected.addAll(following)
        expected.remove(Token.EPSILON)
        while (ctx != null && ctx.invokingState >= 0 && following.contains(Token.EPSILON)) {
            val invokingState = states[ctx.invokingState]
            val rt = invokingState.transition(0) as RuleTransition
            following = nextTokens(rt.followState!!)
            expected.addAll(following)
            expected.remove(Token.EPSILON)
            ctx = ctx.parent as? RuleContext
        }
        if (following.contains(Token.EPSILON)) {
            expected.add(Token.EOF)
        }
        return expected
    }

    companion object {
        const val INVALID_ALT_NUMBER: Int = 0
    }
}
