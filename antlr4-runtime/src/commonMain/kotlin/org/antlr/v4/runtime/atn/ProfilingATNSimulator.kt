/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.TokenStream
import org.antlr.v4.runtime.dfa.DFA
import org.antlr.v4.runtime.dfa.DFAState
import org.antlr.v4.runtime.misc.BitSet

/**
 * @since 4.3
 */
class ProfilingATNSimulator(
    parser: Parser,
) : ParserATNSimulator(
        parser,
        parser.interpreter.atn,
        parser.interpreter.decisionToDFA,
        parser.interpreter.sharedContextCache,
    ) {
    protected val decisions: Array<DecisionInfo>
    protected var numDecisions: Int

    protected var _sllStopIndex: Int = 0
    protected var _llStopIndex: Int = 0

    protected var currentDecision: Int = 0
    protected var currentState: DFAState? = null

    /** At the point of LL failover, we record how SLL would resolve the conflict so that
     * we can determine whether or not a decision / input pair is context-sensitive.
     * If LL gives a different result than SLL's predicted alternative, we have a
     * context sensitivity for sure. The converse is not necessarily true, however.
     * It's possible that after conflict resolution chooses minimum alternatives,
     * SLL could get the same answer as LL. Regardless of whether or not the result indicates
     * an ambiguity, it is not treated as a context sensitivity because LL prediction
     * was not required in order to produce a correct prediction for this decision and input sequence.
     * It may in fact still be a context sensitivity but we don't know by looking at the
     * minimum alternatives for the current input.
     */
    protected var conflictingAltResolvedBySLL: Int = 0

    init {
        numDecisions = atn.decisionToState.size
        decisions = Array(numDecisions) { DecisionInfo(it) }
    }

    fun adaptivePredict(
        input: TokenStream?,
        decision: Int,
        outerContext: ParserRuleContext?,
    ): Int {
        try {
            this._sllStopIndex = -1
            this._llStopIndex = -1
            this.currentDecision = decision
            val start: Long = System.nanoTime() // expensive but useful info
            val alt: Int = super.adaptivePredict(input, decision, outerContext)
            val stop: Long = System.nanoTime()
            decisions[decision].timeInPrediction += (stop - start)
            decisions[decision].invocations++

            val SLL_k: Int = _sllStopIndex - _startIndex + 1
            decisions[decision].SLL_TotalLook += SLL_k
            decisions[decision].SLL_MinLook =
                if (decisions[decision].SLL_MinLook === 0) SLL_k else minOf(decisions[decision].SLL_MinLook, SLL_k)
            if (SLL_k > decisions[decision].SLL_MaxLook) {
                decisions[decision].SLL_MaxLook = SLL_k
                decisions[decision].SLL_MaxLookEvent =
                    LookaheadEventInfo(decision, null, alt, input, _startIndex, _sllStopIndex, false)
            }

            if (_llStopIndex >= 0) {
                val LL_k: Int = _llStopIndex - _startIndex + 1
                decisions[decision].LL_TotalLook += LL_k
                decisions[decision].LL_MinLook =
                    if (decisions[decision].LL_MinLook === 0) LL_k else minOf(decisions[decision].LL_MinLook, LL_k)
                if (LL_k > decisions[decision].LL_MaxLook) {
                    decisions[decision].LL_MaxLook = LL_k
                    decisions[decision].LL_MaxLookEvent =
                        LookaheadEventInfo(decision, null, alt, input, _startIndex, _llStopIndex, true)
                }
            }

            return alt
        } finally {
            this.currentDecision = -1
        }
    }

    protected fun getExistingTargetState(
        previousD: DFAState,
        t: Int,
    ): DFAState? {
        // this method is called after each time the input position advances
        // during SLL prediction
        _sllStopIndex = _input.index()

        val existingTargetState: DFAState? = super.getExistingTargetState(previousD, t)
        if (existingTargetState != null) {
            decisions[currentDecision].SLL_DFATransitions++ // count only if we transition over a DFA state
            if (existingTargetState === ERROR) {
                decisions[currentDecision].errors.add(
                    ErrorInfo(currentDecision, previousD.configs, _input, _startIndex, _sllStopIndex, false),
                )
            }
        }

        currentState = existingTargetState
        return existingTargetState
    }

    protected fun computeTargetState(
        dfa: DFA?,
        previousD: DFAState?,
        t: Int,
    ): DFAState? {
        val state: DFAState? = super.computeTargetState(dfa, previousD, t)
        currentState = state
        return state
    }

    protected fun computeReachSet(
        closure: ATNConfigSet?,
        t: Int,
        fullCtx: Boolean,
    ): ATNConfigSet? {
        if (fullCtx) {
            // this method is called after each time the input position advances
            // during full context prediction
            _llStopIndex = _input.index()
        }

        val reachConfigs: ATNConfigSet? = super.computeReachSet(closure, t, fullCtx)
        if (fullCtx) {
            decisions[currentDecision].LL_ATNTransitions++ // count computation even if error
            if (reachConfigs != null) {
            } else { // no reach on current lookahead symbol. ERROR.
                // TODO: does not handle delayed errors per getSynValidOrSemInvalidAltThatFinishedDecisionEntryRule()
                decisions[currentDecision].errors.add(
                    ErrorInfo(currentDecision, closure, _input, _startIndex, _llStopIndex, true),
                )
            }
        } else {
            decisions[currentDecision].SLL_ATNTransitions++
            if (reachConfigs != null) {
            } else { // no reach on current lookahead symbol. ERROR.
                decisions[currentDecision].errors.add(
                    ErrorInfo(currentDecision, closure, _input, _startIndex, _sllStopIndex, false),
                )
            }
        }
        return reachConfigs
    }

    protected fun evalSemanticContext(
        pred: SemanticContext?,
        parserCallStack: ParserRuleContext?,
        alt: Int,
        fullCtx: Boolean,
    ): Boolean {
        val result: Boolean = super.evalSemanticContext(pred, parserCallStack, alt, fullCtx)
        if (pred !is SemanticContext.PrecedencePredicate) {
            val fullContext = _llStopIndex >= 0
            val stopIndex = if (fullContext) _llStopIndex else _sllStopIndex
            decisions[currentDecision].predicateEvals.add(
                PredicateEvalInfo(currentDecision, _input, _startIndex, stopIndex, pred, result, alt, fullCtx),
            )
        }

        return result
    }

    protected fun reportAttemptingFullContext(
        dfa: DFA?,
        conflictingAlts: BitSet?,
        configs: ATNConfigSet,
        startIndex: Int,
        stopIndex: Int,
    ) {
        if (conflictingAlts != null) {
            conflictingAltResolvedBySLL = conflictingAlts.nextSetBit(0)
        } else {
            conflictingAltResolvedBySLL = configs.getAlts().nextSetBit(0)
        }
        decisions[currentDecision].LL_Fallback++
        super.reportAttemptingFullContext(dfa, conflictingAlts, configs, startIndex, stopIndex)
    }

    protected fun reportContextSensitivity(
        dfa: DFA?,
        prediction: Int,
        configs: ATNConfigSet?,
        startIndex: Int,
        stopIndex: Int,
    ) {
        if (prediction != conflictingAltResolvedBySLL) {
            decisions[currentDecision].contextSensitivities.add(
                ContextSensitivityInfo(currentDecision, configs, _input, startIndex, stopIndex),
            )
        }
        super.reportContextSensitivity(dfa, prediction, configs, startIndex, stopIndex)
    }

    protected fun reportAmbiguity(
        dfa: DFA?,
        D: DFAState?,
        startIndex: Int,
        stopIndex: Int,
        exact: Boolean,
        ambigAlts: BitSet?,
        configs: ATNConfigSet,
    ) {
        val prediction: Int
        if (ambigAlts != null) {
            prediction = ambigAlts.nextSetBit(0)
        } else {
            prediction = configs.getAlts().nextSetBit(0)
        }
        if (configs.fullCtx && prediction != conflictingAltResolvedBySLL) {
            // Even though this is an ambiguity we are reporting, we can
            // still detect some context sensitivities.  Both SLL and LL
            // are showing a conflict, hence an ambiguity, but if they resolve
            // to different minimum alternatives we have also identified a
            // context sensitivity.
            decisions[currentDecision].contextSensitivities.add(
                ContextSensitivityInfo(currentDecision, configs, _input, startIndex, stopIndex),
            )
        }
        decisions[currentDecision].ambiguities.add(
            AmbiguityInfo(
                currentDecision,
                configs,
                ambigAlts,
                _input,
                startIndex,
                stopIndex,
                configs.fullCtx,
            ),
        )
        super.reportAmbiguity(dfa, D, startIndex, stopIndex, exact, ambigAlts, configs)
    }

    val decisionInfo: Array<DecisionInfo>
        // ---------------------------------------------------------------------
        get() = decisions

    fun getCurrentState(): DFAState? = currentState
}
