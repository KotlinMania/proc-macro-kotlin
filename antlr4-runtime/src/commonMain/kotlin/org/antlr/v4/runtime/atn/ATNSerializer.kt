/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.assert
import org.antlr.v4.runtime.misc.IntList
import org.antlr.v4.runtime.misc.IntervalSet

/** This class represents a target neutral serializer for ATNs. An ATN is converted to a list of integers
 * that can be converted back to and ATN. We compute the list of integers and then generate an array
 * into the target language for a particular lexer or parser.  Java is a special case where we must
 * generate strings instead of arrays, but that is handled outside of this class.
 * See [ATNDeserializer.encodeIntsWith16BitWords] and
 * [org.antlr.v4.codegen.model.SerializedJavaATN].
 */
class ATNSerializer(
    atn: ATN,
) {
    var atn: ATN

    private val data: IntList = IntList()

    /** Note that we use a LinkedHashMap as a set to mainintain insertion order while deduplicating
     * entries with the same key.  */
    private val sets: MutableMap<IntervalSet?, Boolean?> = LinkedHashMap()
    private val nonGreedyStates: IntList = IntList()
    private val precedenceStates: IntList = IntList()

    init {
        checkNotNull(atn.grammarType)
        this.atn = atn
    }

    /** Serialize state descriptors, edge descriptors, and decisionstate map
     * into list of ints.  Likely out of date, but keeping as it could be helpful:
     *
     * SERIALIZED_VERSION
     * UUID (2 longs)
     * grammar-type, (ANTLRParser.LEXER, ...)
     * max token type,
     * num states,
     * state-0-type ruleIndex, state-1-type ruleIndex, ... state-i-type ruleIndex optional-arg ...
     * num rules,
     * rule-1-start-state rule-1-args, rule-2-start-state  rule-2-args, ...
     * (args are token type,actionIndex in lexer else 0,0)
     * num modes,
     * mode-0-start-state, mode-1-start-state, ... (parser has 0 modes)
     * num unicode-bmp-sets
     * bmp-set-0-interval-count intervals, bmp-set-1-interval-count intervals, ...
     * num unicode-smp-sets
     * smp-set-0-interval-count intervals, smp-set-1-interval-count intervals, ...
     * num total edges,
     * src, trg, edge-type, edge arg1, optional edge arg2 (present always), ...
     * num decisions,
     * decision-0-start-state, decision-1-start-state, ...
     *
     * Convenient to pack into unsigned shorts to make as Java string.
     */
    fun serialize(): IntList {
        addPreamble()
        val nedges = addEdges()
        addNonGreedyStates()
        addPrecedenceStates()
        addRuleStatesAndLexerTokenTypes()
        addModeStartStates()
        var setIndices: Map<IntervalSet?, Int?>? = null
        setIndices = addSets()
        addEdges(nedges, setIndices)
        addDecisionStartStates()
        addLexerActions()

        return data
    }

    private fun addPreamble() {
        data.add(ATNDeserializer.SERIALIZED_VERSION)

        // convert grammar type to ATN const to avoid dependence on ANTLRParser
        data.add(atn.grammarType.ordinal)
        data.add(atn.maxTokenType)
    }

    private fun addLexerActions() {
        if (atn.grammarType === ATNType.LEXER) {
            data.add(atn.lexerActions.size)
            for (action in atn.lexerActions) {
                val action = action!!
                data.add(action.actionType.ordinal)
                when (action.actionType) {
                    LexerActionType.CHANNEL -> {
                        val channel: Int = (action as LexerChannelAction).channel
                        data.add(channel)
                        data.add(0)
                    }

                    LexerActionType.CUSTOM -> {
                        val ruleIndex: Int = (action as LexerCustomAction).ruleIndex
                        val actionIndex: Int = (action).actionIndex
                        data.add(ruleIndex)
                        data.add(actionIndex)
                    }

                    LexerActionType.MODE -> {
                        val mode: Int = (action as LexerModeAction).mode
                        data.add(mode)
                        data.add(0)
                    }

                    LexerActionType.MORE -> {
                        data.add(0)
                        data.add(0)
                    }

                    LexerActionType.POP_MODE -> {
                        data.add(0)
                        data.add(0)
                    }

                    LexerActionType.PUSH_MODE -> {
                        val pushMode: Int = (action as LexerPushModeAction).mode
                        data.add(pushMode)
                        data.add(0)
                    }

                    LexerActionType.SKIP -> {
                        data.add(0)
                        data.add(0)
                    }

                    LexerActionType.TYPE -> {
                        val type: Int = (action as LexerTypeAction).type
                        data.add(type)
                        data.add(0)
                    }

                    else -> {
                        val message: String =
                            "The specified lexer action type ${action.actionType} is not valid."
                        throw IllegalArgumentException(message)
                    }
                }
            }
        }
    }

    private fun addDecisionStartStates() {
        val ndecisions: Int = atn.decisionToState.size
        data.add(ndecisions)
        for (decStartState in atn.decisionToState) {
            data.add(decStartState.stateNumber)
        }
    }

    private fun addEdges(
        nedges: Int,
        setIndices: Map<IntervalSet?, Int?>,
    ) {
        data.add(nedges)
        for (s in atn.states) {
            if (s.stateType == ATNState.RULE_STOP) {
                continue
            }

            for (i in 0..<s.numberOfTransitions) {
                val t: Transition = s.transition(i)

                checkNotNull(atn.states.get(t.target.stateNumber)) { "Cannot serialize a transition to a removed state." }

                val src: Int = s.stateNumber
                var trg: Int = t.target.stateNumber
                val edgeType: Int = Transition.serializationTypes.get(t::class)!!
                var arg1 = 0
                var arg2 = 0
                var arg3 = 0
                when (edgeType) {
                    Transition.RULE -> {
                        trg = (t as RuleTransition).followState.stateNumber
                        arg1 = (t).target.stateNumber
                        arg2 = (t).ruleIndex
                        arg3 = (t).precedence
                    }

                    Transition.PRECEDENCE -> {
                        val ppt: PrecedencePredicateTransition = t as PrecedencePredicateTransition
                        arg1 = ppt.precedence
                    }

                    Transition.PREDICATE -> {
                        val pt: PredicateTransition = t as PredicateTransition
                        arg1 = pt.ruleIndex
                        arg2 = pt.predIndex
                        arg3 = if (pt.isCtxDependent) 1 else 0
                    }

                    Transition.RANGE -> {
                        arg1 = (t as RangeTransition).from
                        arg2 = (t).to
                        if (arg1 == Token.EOF) {
                            arg1 = 0
                            arg3 = 1
                        }
                    }

                    Transition.ATOM -> {
                        arg1 = (t as AtomTransition).label
                        if (arg1 == Token.EOF) {
                            arg1 = 0
                            arg3 = 1
                        }
                    }

                    Transition.ACTION -> {
                        val at: ActionTransition = t as ActionTransition
                        arg1 = at.ruleIndex
                        arg2 = at.actionIndex
                        arg3 = if (at.isCtxDependent) 1 else 0
                    }

                    Transition.SET -> arg1 = setIndices[(t as SetTransition).set]!!
                    Transition.NOT_SET -> arg1 = setIndices[(t as SetTransition).set]!!
                    Transition.WILDCARD -> {}
                }

                data.add(src)
                data.add(trg)
                data.add(edgeType)
                data.add(arg1)
                data.add(arg2)
                data.add(arg3)
            }
        }
    }

    private fun addSets(): Map<IntervalSet?, Int?> {
        org.antlr.v4.runtime.atn.ATNSerializer.Companion
            .serializeSets(data, sets.keys)
        val setIndices: MutableMap<IntervalSet?, Int?> = HashMap()
        var setIndex = 0
        for (s in sets.keys) {
            setIndices[s] = setIndex++
        }
        return setIndices
    }

    private fun addModeStartStates() {
        val nmodes: Int = atn.modeToStartState.size
        data.add(nmodes)
        if (nmodes > 0) {
            for (modeStartState in atn.modeToStartState) {
                data.add(modeStartState.stateNumber)
            }
        }
    }

    private fun addRuleStatesAndLexerTokenTypes() {
        val nrules: Int = atn.ruleToStartState.size
        data.add(nrules)
        for (r in 0..<nrules) {
            val ruleStartState: ATNState = atn.ruleToStartState[r]!!
            data.add(ruleStartState.stateNumber)
            if (atn.grammarType === ATNType.LEXER) {
                assert(
                    atn.ruleToTokenType[r] >= 0, // 0 implies fragment rule, other token types > 0
                )
                data.add(atn.ruleToTokenType[r])
            }
        }
    }

    private fun addPrecedenceStates() {
        data.add(precedenceStates.size())
        for (i in 0..<precedenceStates.size()) {
            data.add(precedenceStates.get(i))
        }
    }

    private fun addNonGreedyStates() {
        data.add(nonGreedyStates.size())
        for (i in 0..<nonGreedyStates.size()) {
            data.add(nonGreedyStates.get(i))
        }
    }

    private fun addEdges(): Int {
        var nedges = 0
        data.add(atn.states.size)
        for (s in atn.states) {
            val stateType: Int = s.stateType
            if (s is DecisionState && (s).nonGreedy) {
                nonGreedyStates.add(s.stateNumber)
            }

            if (s is RuleStartState && (s).isLeftRecursiveRule) {
                precedenceStates.add(s.stateNumber)
            }

            data.add(stateType)

            data.add(s.ruleIndex)

            if (s.stateType == ATNState.LOOP_END) {
                data.add((s as LoopEndState).loopBackState!!.stateNumber)
            } else if (s is BlockStartState) {
                data.add((s).endState!!.stateNumber)
            }

            if (s.stateType != ATNState.RULE_STOP) {
                // the deserializer can trivially derive these edges, so there's no need to serialize them
                nedges += s.numberOfTransitions
            }

            for (i in 0..<s.numberOfTransitions) {
                val t: Transition = s.transition(i)
                val edgeType: Int = Transition.serializationTypes.get(t::class)!!
                if (edgeType == Transition.SET || edgeType == Transition.NOT_SET) {
                    val st: SetTransition = t as SetTransition
                    sets[st.set] = true
                }
            }
        }
        return nedges
    }

    companion object {
        private fun serializeSets(
            data: IntList,
            sets: Collection<IntervalSet?>,
        ) {
            val nSets: Int = sets.size
            data.add(nSets)

            for (set in sets) {
                val s = set!!
                val containsEof: Boolean = s.contains(Token.EOF)
                if (containsEof && s.intervals.get(0).b == Token.EOF) {
                    data.add(s.intervals.size - 1)
                } else {
                    data.add(s.intervals.size)
                }

                data.add(if (containsEof) 1 else 0)
                for (I in s.intervals) {
                    if (I.a == Token.EOF) {
                        if (I.b == Token.EOF) {
                            continue
                        } else {
                            data.add(0)
                        }
                    } else {
                        data.add(I.a)
                    }
                    data.add(I.b)
                }
            }
        }

        fun getSerialized(atn: ATN): IntList =
            org.antlr.v4.runtime.atn
                .ATNSerializer(atn)
                .serialize()
    }
}
