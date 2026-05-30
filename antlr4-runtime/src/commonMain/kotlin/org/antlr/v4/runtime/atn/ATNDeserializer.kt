/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.misc.IntList
import org.antlr.v4.runtime.misc.IntervalSet
import org.antlr.v4.runtime.misc.Pair

/** Deserialize ATNs for JavaTarget; it's complicated by the fact that java requires
 * that we serialize the list of integers as 16 bit characters in a string. Other
 * targets will have an array of ints generated and can simply decode the ints
 * back into an ATN.
 *
 * @author Sam Harwell
 */
class ATNDeserializer
    @kotlin.jvm.JvmOverloads
    constructor(
        deserializationOptions: ATNDeserializationOptions? = ATNDeserializationOptions.defaultOptions,
    ) {
        private val deserializationOptions: ATNDeserializationOptions?

        init {
            var deserializationOptions: ATNDeserializationOptions? = deserializationOptions
            if (deserializationOptions == null) {
                deserializationOptions = ATNDeserializationOptions.defaultOptions
            }

            this.deserializationOptions = deserializationOptions
        }

        fun deserialize(data: CharArray): ATN = deserialize(decodeIntsEncodedAs16BitWords(data))

        fun deserialize(data: IntArray): ATN {
            var p = 0
            val version = data[p++]
            if (version != org.antlr.v4.runtime.atn.ATNDeserializer.Companion.SERIALIZED_VERSION) {
                val reason: String? =
                    "Could not deserialize ATN with version $version (expected ${org.antlr.v4.runtime.atn.ATNDeserializer.Companion.SERIALIZED_VERSION})."
                throw UnsupportedOperationException(reason!!)
            }

            val grammarType: ATNType = ATNType.entries[data[p++]]
            val maxTokenType = data[p++]
            val atn: ATN = ATN(grammarType, maxTokenType)

            //
            // STATES
            //
            val loopBackStateNumbers: MutableList<Pair<LoopEndState?, Int?>> = ArrayList()
            val endStateNumbers: MutableList<Pair<BlockStartState?, Int?>> = ArrayList()
            val nstates = data[p++]
            for (i in 0..<nstates) {
                val stype = data[p++]
                // ignore bad type of states
                if (stype == ATNState.INVALID_TYPE) {
                    atn.addState(null)
                    continue
                }

                val ruleIndex = data[p++]
                val s: ATNState? = stateFactory(stype, ruleIndex)
                if (stype == ATNState.LOOP_END) { // special case
                    val loopBackStateNumber = data[p++]
                    loopBackStateNumbers.add(Pair<LoopEndState?, Int?>(s as LoopEndState?, loopBackStateNumber))
                } else if (s is BlockStartState) {
                    val endStateNumber = data[p++]
                    endStateNumbers.add(Pair<BlockStartState?, Int?>(s as BlockStartState?, endStateNumber))
                }
                atn.addState(s)
            }

            // delay the assignment of loop back and end states until we know all the state instances have been initialized
            for (pair in loopBackStateNumbers) {
                pair.a!!.loopBackState = atn.states[pair.b!!]
            }

            for (pair in endStateNumbers) {
                pair.a!!.endState = atn.states[pair.b!!] as BlockEndState?
            }

            val numNonGreedyStates = data[p++]
            for (i in 0..<numNonGreedyStates) {
                val stateNumber = data[p++]
                (atn.states.get(stateNumber) as DecisionState).nonGreedy = true
            }

            val numPrecedenceStates = data[p++]
            for (i in 0..<numPrecedenceStates) {
                val stateNumber = data[p++]
                (atn.states.get(stateNumber) as RuleStartState).isLeftRecursiveRule = true
            }

            //
            // RULES
            //
            val nrules = data[p++]
            if (atn.grammarType === ATNType.LEXER) {
                atn.ruleToTokenType = IntArray(nrules)
            }

            atn.ruleToStartState = arrayOfNulls<RuleStartState>(nrules)
            for (i in 0..<nrules) {
                val s = data[p++]
                val startState: RuleStartState? = atn.states.get(s) as RuleStartState?
                atn.ruleToStartState[i] = startState
                if (atn.grammarType === ATNType.LEXER) {
                    val tokenType = data[p++]
                    atn.ruleToTokenType[i] = tokenType
                }
            }

            atn.ruleToStopState = arrayOfNulls<RuleStopState>(nrules)
            for (state in atn.states) {
                if (state !is RuleStopState) {
                    continue
                }

                val stopState: RuleStopState? = state as RuleStopState?
                atn.ruleToStopState[state.ruleIndex] = stopState
                atn.ruleToStartState[state.ruleIndex]!!.stopState = stopState
            }

            //
            // MODES
            //
            val nmodes = data[p++]
            for (i in 0..<nmodes) {
                val s = data[p++]
                atn.modeToStartState.add(atn.states[s] as TokensStartState)
            }

            //
            // SETS
            //
            val sets: MutableList<IntervalSet?> = ArrayList()
            p = deserializeSets(data, p, sets)

            //
            // EDGES
            //
            val nedges = data[p++]
            for (i in 0..<nedges) {
                val src = data[p]
                val trg = data[p + 1]
                val ttype = data[p + 2]
                val arg1 = data[p + 3]
                val arg2 = data[p + 4]
                val arg3 = data[p + 5]
                val trans: Transition? = edgeFactory(atn, ttype, src, trg, arg1, arg2, arg3, sets)
                // 			println("EDGE "+trans::class.getSimpleName()+" "+
// 							   src+"->"+trg+
// 					   " "+Transition.serializationNames[ttype]+
// 					   " "+arg1+","+arg2+","+arg3);
                val srcState: ATNState = atn.states.get(src)
                srcState.addTransition(trans!!)
                p += 6
            }

            // edges for rule stop states can be derived, so they aren't serialized
            for (state in atn.states) {
                for (i in 0..<state.numberOfTransitions) {
                    val t: Transition? = state.transition(i)
                    if (t !is RuleTransition) {
                        continue
                    }

                    val ruleTransition: RuleTransition = t
                    var outermostPrecedenceReturn = -1
                    if (atn.ruleToStartState[ruleTransition.target.ruleIndex]!!.isLeftRecursiveRule) {
                        if (ruleTransition.precedence == 0) {
                            outermostPrecedenceReturn = ruleTransition.target.ruleIndex
                        }
                    }

                    val returnTransition: EpsilonTransition =
                        EpsilonTransition(ruleTransition.followState, outermostPrecedenceReturn)
                    atn.ruleToStopState[ruleTransition.target.ruleIndex]!!.addTransition(returnTransition)
                }
            }

            for (state in atn.states) {
                if (state is BlockStartState) {
                    // we need to know the end state to set its start state
                    checkNotNull((state).endState)

                    // block end states can only be associated to a single block start state
                    check((state).endState!!.startState == null)

                    (state).endState!!.startState = state
                }

                if (state is PlusLoopbackState) {
                    val loopbackState: PlusLoopbackState = state
                    for (i in 0..<loopbackState.numberOfTransitions) {
                        val target: ATNState = loopbackState.transition(i).target
                        if (target is PlusBlockStartState) {
                            (target).loopBackState = loopbackState
                        }
                    }
                } else if (state is StarLoopbackState) {
                    val loopbackState: StarLoopbackState = state
                    for (i in 0..<loopbackState.numberOfTransitions) {
                        val target: ATNState = loopbackState.transition(i).target
                        if (target is StarLoopEntryState) {
                            (target).loopBackState = loopbackState
                        }
                    }
                }
            }

            //
            // DECISIONS
            //
            val ndecisions = data[p++]
            for (i in 1..ndecisions) {
                val s = data[p++]
                val decState: DecisionState = atn.states.get(s) as DecisionState
                atn.decisionToState.add(decState)
                decState.decision = i - 1
            }

            //
            // LEXER ACTIONS
            //
            if (atn.grammarType === ATNType.LEXER) {
                atn.lexerActions = arrayOfNulls<LexerAction>(data[p++])
                for (i in 0..<atn.lexerActions.size) {
                    val actionType: LexerActionType = LexerActionType.values()[data[p++]]
                    val data1 = data[p++]
                    val data2 = data[p++]

                    val lexerAction: LexerAction? = lexerActionFactory(actionType, data1, data2)

                    atn.lexerActions[i] = lexerAction
                }
            }

            markPrecedenceDecisions(atn)

            if (deserializationOptions!!.isVerifyATN()) {
                verifyATN(atn)
            }

            if (deserializationOptions.isGenerateRuleBypassTransitions() && atn.grammarType === ATNType.PARSER) {
                atn.ruleToTokenType = IntArray(atn.ruleToStartState.size)
                for (i in 0..<atn.ruleToStartState.size) {
                    atn.ruleToTokenType[i] = atn.maxTokenType + i + 1
                }

                for (i in 0..<atn.ruleToStartState.size) {
                    val bypassStart: BasicBlockStartState = BasicBlockStartState()
                    bypassStart.ruleIndex = i
                    atn.addState(bypassStart)

                    val bypassStop: BlockEndState = BlockEndState()
                    bypassStop.ruleIndex = i
                    atn.addState(bypassStop)

                    bypassStart.endState = bypassStop
                    atn.defineDecisionState(bypassStart)

                    bypassStop.startState = bypassStart

                    var endState: ATNState?
                    var excludeTransition: Transition? = null
                    if (atn.ruleToStartState[i]!!.isLeftRecursiveRule) {
                        // wrap from the beginning of the rule to the StarLoopEntryState
                        endState = null
                        for (state in atn.states) {
                            if (state.ruleIndex != i) {
                                continue
                            }

                            if (state !is StarLoopEntryState) {
                                continue
                            }

                            val maybeLoopEndState: ATNState = state.transition(state.numberOfTransitions - 1).target
                            if (maybeLoopEndState !is LoopEndState) {
                                continue
                            }

                            if (maybeLoopEndState.epsilonOnlyTransitions && maybeLoopEndState.transition(0).target is RuleStopState) {
                                endState = state
                                break
                            }
                        }

                        if (endState == null) {
                            throw UnsupportedOperationException("Couldn't identify final state of the precedence rule prefix section.")
                        }

                        excludeTransition = (endState as StarLoopEntryState).loopBackState!!.transition(0)
                    } else {
                        endState = atn.ruleToStopState[i]
                    }

                    // all non-excluded transitions that currently target end state need to target blockEnd instead
                    for (state in atn.states) {
                        for (transition in state.transitions) {
                            if (transition === excludeTransition) {
                                continue
                            }

                            if (transition.target === endState) {
                                transition.target = bypassStop
                            }
                        }
                    }

                    // all transitions leaving the rule start state need to leave blockStart instead
                    while (atn.ruleToStartState[i]!!.numberOfTransitions > 0) {
                        val transition: Transition? =
                            atn.ruleToStartState[i]!!.removeTransition(atn.ruleToStartState[i]!!.numberOfTransitions - 1)
                        bypassStart.addTransition(transition!!)
                    }

                    // link the new states
                    atn.ruleToStartState[i]!!.addTransition(EpsilonTransition(bypassStart))
                    bypassStop.addTransition(EpsilonTransition(endState!!))

                    val matchState: ATNState = BasicState()
                    atn.addState(matchState)
                    matchState.addTransition(AtomTransition(bypassStop, atn.ruleToTokenType[i]))
                    bypassStart.addTransition(EpsilonTransition(matchState))
                }

                if (deserializationOptions.isVerifyATN()) {
                    // reverify after modification
                    verifyATN(atn)
                }
            }

            return atn
        }

        private fun deserializeSets(
            data: IntArray,
            p: Int,
            sets: MutableList<IntervalSet?>,
        ): Int {
            var p = p
            val nsets = data[p++]
            for (i in 0..<nsets) {
                val nintervals = data[p]
                p++
                val set: IntervalSet = IntervalSet()
                sets.add(set)

                val containsEof = data[p++] != 0
                if (containsEof) {
                    set.add(-1)
                }

                for (idx in 0..<nintervals) {
                    val a = data[p++]
                    val b = data[p++]
                    set.add(a, b)
                }
            }
            return p
        }

        /**
         * Analyze the [StarLoopEntryState] states in the specified ATN to set
         * the [StarLoopEntryState.isPrecedenceDecision] field to the
         * correct value.
         *
         * @param atn The ATN.
         */
        protected fun markPrecedenceDecisions(atn: ATN) {
            for (state in atn.states) {
                if (state !is StarLoopEntryState) {
                    continue
                }

            /* We analyze the ATN to determine if this ATN decision state is the
             * decision for the closure block that determines whether a
             * precedence rule should continue or complete.
             */
                if (atn.ruleToStartState[state.ruleIndex]!!.isLeftRecursiveRule) {
                    val maybeLoopEndState: ATNState = state.transition(state.numberOfTransitions - 1).target
                    if (maybeLoopEndState is LoopEndState) {
                        if (maybeLoopEndState.epsilonOnlyTransitions && maybeLoopEndState.transition(0).target is RuleStopState) {
                            (state).isPrecedenceDecision = true
                        }
                    }
                }
            }
        }

        protected fun verifyATN(atn: ATN) {
            // verify assumptions
            for (state in atn.states) {
                checkCondition(state.onlyHasEpsilonTransitions() || state.numberOfTransitions <= 1)

                if (state is PlusBlockStartState) {
                    checkCondition((state).loopBackState != null)
                }

                if (state is StarLoopEntryState) {
                    val starLoopEntryState: StarLoopEntryState = state
                    checkCondition(starLoopEntryState.loopBackState != null)
                    checkCondition(starLoopEntryState.numberOfTransitions == 2)

                    if (starLoopEntryState.transition(0).target is StarBlockStartState) {
                        checkCondition(starLoopEntryState.transition(1).target is LoopEndState)
                        checkCondition(!starLoopEntryState.nonGreedy)
                    } else if (starLoopEntryState.transition(0).target is LoopEndState) {
                        checkCondition(starLoopEntryState.transition(1).target is StarBlockStartState)
                        checkCondition(starLoopEntryState.nonGreedy)
                    } else {
                        throw IllegalStateException("")
                    }
                }

                if (state is StarLoopbackState) {
                    checkCondition(state.numberOfTransitions == 1)
                    checkCondition(state.transition(0).target is StarLoopEntryState)
                }

                if (state is LoopEndState) {
                    checkCondition((state).loopBackState != null)
                }

                if (state is RuleStartState) {
                    checkCondition((state).stopState != null)
                }

                if (state is BlockStartState) {
                    checkCondition((state).endState != null)
                }

                if (state is BlockEndState) {
                    checkCondition((state).startState != null)
                }

                if (state is DecisionState) {
                    val decisionState: DecisionState = state
                    checkCondition(decisionState.numberOfTransitions <= 1 || decisionState.decision >= 0)
                } else {
                    checkCondition(state.numberOfTransitions <= 1 || state is RuleStopState)
                }
            }
        }

        protected fun checkCondition(
            condition: Boolean,
            message: String? = null,
        ) {
            if (!condition) {
                throw IllegalStateException(message)
            }
        }

        protected fun edgeFactory(
            atn: ATN,
            type: Int,
            src: Int,
            trg: Int,
            arg1: Int,
            arg2: Int,
            arg3: Int,
            sets: MutableList<IntervalSet?>,
        ): Transition? {
            val target: ATNState = atn.states[trg]
            when (type) {
                Transition.EPSILON -> return EpsilonTransition(target)
                Transition.RANGE ->
                    if (arg3 != 0) {
                        return RangeTransition(target, Token.EOF, arg2)
                    } else {
                        return RangeTransition(target, arg1, arg2)
                    }

                Transition.RULE -> {
                    val rt: RuleTransition = RuleTransition(atn.states[arg1] as RuleStartState, arg2, arg3, target)
                    return rt
                }

                Transition.PREDICATE -> {
                    val pt: PredicateTransition = PredicateTransition(target, arg1, arg2, arg3 != 0)
                    return pt
                }

                Transition.PRECEDENCE -> return PrecedencePredicateTransition(target, arg1)
                Transition.ATOM ->
                    if (arg3 != 0) {
                        return AtomTransition(target, Token.EOF)
                    } else {
                        return AtomTransition(target, arg1)
                    }

                Transition.ACTION -> {
                    val a: ActionTransition = ActionTransition(target, arg1, arg2, arg3 != 0)
                    return a
                }

                Transition.SET -> return SetTransition(target, sets[arg1]!!)
                Transition.NOT_SET -> return NotSetTransition(target, sets[arg1]!!)
                Transition.WILDCARD -> return WildcardTransition(target)
            }

            throw IllegalArgumentException("The specified transition type is not valid.")
        }

        protected fun stateFactory(
            type: Int,
            ruleIndex: Int,
        ): ATNState? {
            val s: ATNState?
            when (type) {
                ATNState.INVALID_TYPE -> return null
                ATNState.BASIC -> s = BasicState()
                ATNState.RULE_START -> s = RuleStartState()
                ATNState.BLOCK_START -> s = BasicBlockStartState()
                ATNState.PLUS_BLOCK_START -> s = PlusBlockStartState()
                ATNState.STAR_BLOCK_START -> s = StarBlockStartState()
                ATNState.TOKEN_START -> s = TokensStartState()
                ATNState.RULE_STOP -> s = RuleStopState()
                ATNState.BLOCK_END -> s = BlockEndState()
                ATNState.STAR_LOOP_BACK -> s = StarLoopbackState()
                ATNState.STAR_LOOP_ENTRY -> s = StarLoopEntryState()
                ATNState.PLUS_LOOP_BACK -> s = PlusLoopbackState()
                ATNState.LOOP_END -> s = LoopEndState()
                else -> {
                    val message: String? =
                        "The specified state type $type is not valid."
                    throw IllegalArgumentException(message)
                }
            }

            s.ruleIndex = ruleIndex
            return s
        }

        protected fun lexerActionFactory(
            type: LexerActionType,
            data1: Int,
            data2: Int,
        ): LexerAction? {
            when (type) {
                LexerActionType.CHANNEL -> return LexerChannelAction(data1)

                LexerActionType.CUSTOM -> return LexerCustomAction(data1, data2)

                LexerActionType.MODE -> return LexerModeAction(data1)

                LexerActionType.MORE -> return LexerMoreAction.INSTANCE

                LexerActionType.POP_MODE -> return LexerPopModeAction.INSTANCE

                LexerActionType.PUSH_MODE -> return LexerPushModeAction(data1)

                LexerActionType.SKIP -> return LexerSkipAction.INSTANCE

                LexerActionType.TYPE -> return LexerTypeAction(data1)

                else -> throw IllegalArgumentException(
                    "The specified lexer action type $type is not valid.",
                )
            }
        }

        companion object {
            const val SERIALIZED_VERSION = 4

            protected fun toInt(c: Char): Int = c.code

            protected fun toInt32(
                data: CharArray,
                offset: Int,
            ): Int = data[offset].code or (data[offset + 1].code shl 16)

            protected fun toInt32(
                data: IntArray,
                offset: Int,
            ): Int = data[offset] or (data[offset + 1] shl 16)

            /** Given a list of integers representing a serialized ATN, encode values too large to fit into 15 bits
             * as two 16bit values. We use the high bit (0x8000_0000) to indicate values requiring two 16 bit words.
             * If the high bit is set, we grab the next value and combine them to get a 31-bit value. The possible
             * input int values are [-1,0x7FFF_FFFF].
             *
             * | compression/encoding                         | uint16 count | type            |
             * | -------------------------------------------- | ------------ | --------------- |
             * | 0xxxxxxx xxxxxxxx                            | 1            | uint (15 bit)   |
             * | 1xxxxxxx xxxxxxxx yyyyyyyy yyyyyyyy          | 2            | uint (16+ bits) |
             * | 11111111 11111111 11111111 11111111          | 2            | int value -1    |
             *
             * This is only used (other than for testing) by [org.antlr.v4.codegen.model.SerializedJavaATN]
             * to encode ints as char values for the java target, but it is convenient to combine it with the
             * #decodeIntsEncodedAs16BitWords that follows as they are a pair (I did not want to introduce a new class
             * into the runtime). Used only for Java Target.
             */
            fun encodeIntsWith16BitWords(data: IntList): IntList {
                val data16: IntList = IntList((data.size() * 1.5).toInt())
                for (i in 0..<data.size()) {
                    var v: Int = data.get(i)
                    if (v == -1) { // use two max uint16 for -1
                        data16.add(0xFFFF)
                        data16.add(0xFFFF)
                    } else if (v <= 0x7FFF) {
                        data16.add(v)
                    } else { // v > 0x7FFF
                        if (v >= 0x7FFFFFFF) { // too big to fit in 15 bits + 16 bits? (+1 would be 8000_0000 which is bad encoding)
                            throw UnsupportedOperationException("Serialized ATN data element[$i] = $v doesn't fit in 31 bits")
                        }
                        v = v and 0x7FFFFFFF // strip high bit (sentinel) if set
                        data16.add((v shr 16) or 0x8000) // store high 15-bit word first and set high bit to say word follows
                        data16.add((v and 0xFFFF)) // then store lower 16-bit word
                    }
                }
                return data16
            }

            /** Convert a list of chars (16 uint) that represent a serialized and compressed list of ints for an ATN.
             * This method pairs with [.encodeIntsWith16BitWords] above. Used only for Java Target.
             */
            @kotlin.jvm.JvmOverloads
            fun decodeIntsEncodedAs16BitWords(
                data16: CharArray,
                trimToSize: Boolean = false,
            ): IntArray {
                // will be strictly smaller but we waste bit of space to avoid copying during initialization of parsers
                val data = IntArray(data16.size)
                var i = 0
                var i2 = 0
                while (i < data16.size) {
                    val v = data16[i++]
                    if ((v.code and 0x8000) == 0) { // hi bit not set? Implies 1-word value
                        data[i2++] = v.code // 7 bit int
                    } else { // hi bit set. Implies 2-word value
                        val vnext = data16[i++]
                        if (v.code == 0xFFFF && vnext.code == 0xFFFF) { // is it -1?
                            data[i2++] = -1
                        } else { // 31-bit int
                            data[i2++] = (v.code and 0x7FFF) shl 16 or (vnext.code and 0xFFFF)
                        }
                    }
                }
                if (trimToSize) {
                    return data.copyOf(i2)
                }
                return data
            }
        }
    }
