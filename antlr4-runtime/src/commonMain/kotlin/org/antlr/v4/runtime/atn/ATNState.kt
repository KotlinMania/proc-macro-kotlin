/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.misc.IntervalSet

/**
 * The following images show the relation of states and
 * [transitions] for various grammar constructs.
 *
 *  * Solid edges marked with an epsilon indicate a required [EpsilonTransition].
 *  * Dashed edges indicate locations where any transition derived from [Transition] might appear.
 *  * Dashed nodes are place holders for either a sequence of linked [BasicState] states
 *    or the inclusion of a block representing a nested construct in one of the forms below.
 *  * Nodes showing multiple outgoing alternatives with a `...` support any number of
 *    alternatives (one or more). Nodes without the `...` only support the exact number
 *    of alternatives shown in the diagram.
 */
abstract class ATNState {
    var atn: ATN? = null

    var stateNumber: Int = INVALID_STATE_NUMBER

    var ruleIndex: Int = 0

    var epsilonOnlyTransitions: Boolean = false

    internal val transitions: MutableList<Transition> = ArrayList(INITIAL_NUM_TRANSITIONS)

    var nextTokenWithinRule: IntervalSet? = null

    override fun hashCode(): Int = stateNumber

    override fun equals(other: Any?): Boolean {
        if (other is ATNState) return stateNumber == other.stateNumber
        return false
    }

    val isNonGreedyExitState: Boolean
        get() = false

    override fun toString(): String = stateNumber.toString()

    fun getTransitions(): Array<Transition> = transitions.toTypedArray()

    val numberOfTransitions: Int
        get() = transitions.size

    fun addTransition(e: Transition) {
        addTransition(transitions.size, e)
    }

    fun addTransition(
        index: Int,
        e: Transition,
    ) {
        if (transitions.isEmpty()) {
            epsilonOnlyTransitions = e.isEpsilon
        } else if (epsilonOnlyTransitions != e.isEpsilon) {
            println("ATN state $stateNumber has both epsilon and non-epsilon transitions.")
            epsilonOnlyTransitions = false
        }

        var alreadyPresent = false
        for (t in transitions) {
            if (t.target.stateNumber == e.target.stateNumber) {
                if (t.label() != null && e.label() != null && t.label() == e.label()) {
                    alreadyPresent = true
                    break
                } else if (t.isEpsilon && e.isEpsilon) {
                    alreadyPresent = true
                    break
                }
            }
        }
        if (!alreadyPresent) {
            transitions.add(index, e)
        }
    }

    fun transition(i: Int): Transition = transitions[i]

    fun setTransition(
        i: Int,
        e: Transition,
    ) {
        transitions[i] = e
    }

    fun removeTransition(index: Int): Transition = transitions.removeAt(index)

    abstract val stateType: Int

    fun onlyHasEpsilonTransitions(): Boolean = epsilonOnlyTransitions

    companion object {
        const val INITIAL_NUM_TRANSITIONS: Int = 4

        const val INVALID_TYPE: Int = 0
        const val BASIC: Int = 1
        const val RULE_START: Int = 2
        const val BLOCK_START: Int = 3
        const val PLUS_BLOCK_START: Int = 4
        const val STAR_BLOCK_START: Int = 5
        const val TOKEN_START: Int = 6
        const val RULE_STOP: Int = 7
        const val BLOCK_END: Int = 8
        const val STAR_LOOP_BACK: Int = 9
        const val STAR_LOOP_ENTRY: Int = 10
        const val PLUS_LOOP_BACK: Int = 11
        const val LOOP_END: Int = 12

        val serializationNames: List<String> =
            listOf(
                "INVALID",
                "BASIC",
                "RULE_START",
                "BLOCK_START",
                "PLUS_BLOCK_START",
                "STAR_BLOCK_START",
                "TOKEN_START",
                "RULE_STOP",
                "BLOCK_END",
                "STAR_LOOP_BACK",
                "STAR_LOOP_ENTRY",
                "PLUS_LOOP_BACK",
                "LOOP_END",
            )

        const val INVALID_STATE_NUMBER: Int = -1
    }
}
