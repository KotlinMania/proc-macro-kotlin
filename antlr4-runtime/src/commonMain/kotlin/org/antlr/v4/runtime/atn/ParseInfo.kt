/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.dfa.DFA

/**
 * This class provides access to specific and aggregate statistics gathered
 * during profiling of a parser.
 *
 * @since 4.3
 */
class ParseInfo(atnSimulator: ProfilingATNSimulator) {
    protected val atnSimulator: ProfilingATNSimulator

    init {
        this.atnSimulator = atnSimulator
    }

    val decisionInfo: Array<DecisionInfo>
        /**
         * Gets an array of [DecisionInfo] instances containing the profiling
         * information gathered for each decision in the ATN.
         *
         * @return An array of [DecisionInfo] instances, indexed by decision
         * number.
         */
        get() = atnSimulator.decisionInfo

    val lLDecisions: List<Int>
        /**
         * Gets the decision numbers for decisions that required one or more
         * full-context predictions during parsing. These are decisions for which
         * [DecisionInfo.LL_Fallback] is non-zero.
         *
         * @return A list of decision numbers which required one or more
         * full-context predictions during parsing.
         */
        get() {
            val decisions: Array<DecisionInfo?> = atnSimulator.decisionInfo
            val LL: MutableList<Int> = ArrayList()()
            for (i in decisions.indices) {
                val fallBack: Long = decisions[i].LL_Fallback
                if (fallBack > 0) LL.add(i)
            }
            return LL
        }

    val totalTimeInPrediction: Long
        /**
         * Gets the total time spent during prediction across all decisions made
         * during parsing. This value is the sum of
         * [DecisionInfo.timeInPrediction] for all decisions.
         */
        get() {
            val decisions: Array<DecisionInfo?> = atnSimulator.decisionInfo
            var t: Long = 0
            for (i in decisions.indices) {
                t += decisions[i].timeInPrediction
            }
            return t
        }

    val totalSLLLookaheadOps: Long
        /**
         * Gets the total number of SLL lookahead operations across all decisions
         * made during parsing. This value is the sum of
         * [DecisionInfo.SLL_TotalLook] for all decisions.
         */
        get() {
            val decisions: Array<DecisionInfo?> = atnSimulator.decisionInfo
            var k: Long = 0
            for (i in decisions.indices) {
                k += decisions[i].SLL_TotalLook
            }
            return k
        }

    val totalLLLookaheadOps: Long
        /**
         * Gets the total number of LL lookahead operations across all decisions
         * made during parsing. This value is the sum of
         * [DecisionInfo.LL_TotalLook] for all decisions.
         */
        get() {
            val decisions: Array<DecisionInfo?> = atnSimulator.decisionInfo
            var k: Long = 0
            for (i in decisions.indices) {
                k += decisions[i].LL_TotalLook
            }
            return k
        }

    val totalSLLATNLookaheadOps: Long
        /**
         * Gets the total number of ATN lookahead operations for SLL prediction
         * across all decisions made during parsing.
         */
        get() {
            val decisions: Array<DecisionInfo?> = atnSimulator.decisionInfo
            var k: Long = 0
            for (i in decisions.indices) {
                k += decisions[i].SLL_ATNTransitions
            }
            return k
        }

    val totalLLATNLookaheadOps: Long
        /**
         * Gets the total number of ATN lookahead operations for LL prediction
         * across all decisions made during parsing.
         */
        get() {
            val decisions: Array<DecisionInfo?> = atnSimulator.decisionInfo
            var k: Long = 0
            for (i in decisions.indices) {
                k += decisions[i].LL_ATNTransitions
            }
            return k
        }

    val totalATNLookaheadOps: Long
        /**
         * Gets the total number of ATN lookahead operations for SLL and LL
         * prediction across all decisions made during parsing.
         *
         *
         *
         * This value is the sum of [.getTotalSLLATNLookaheadOps] and
         * [.getTotalLLATNLookaheadOps].
         */
        get() {
            val decisions: Array<DecisionInfo?> = atnSimulator.decisionInfo
            var k: Long = 0
            for (i in decisions.indices) {
                k += decisions[i].SLL_ATNTransitions
                k += decisions[i].LL_ATNTransitions
            }
            return k
        }

    val dFASize: Int
        /**
         * Gets the total number of DFA states stored in the DFA cache for all
         * decisions in the ATN.
         */
        get() {
            var n = 0
            val decisionToDFA: Array<DFA?> = atnSimulator.decisionToDFA
            for (i in decisionToDFA.indices) {
                n += getDFASize(i)
            }
            return n
        }

    /**
     * Gets the total number of DFA states stored in the DFA cache for a
     * particular decision.
     */
    fun getDFASize(decision: Int): Int {
        val decisionToDFA: DFA = atnSimulator.decisionToDFA[decision]
        return decisionToDFA.states.size
    }
}
