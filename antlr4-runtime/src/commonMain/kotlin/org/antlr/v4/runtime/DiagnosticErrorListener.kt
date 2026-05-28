/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

import org.antlr.v4.runtime.atn.ATNConfigSet
import org.antlr.v4.runtime.dfa.DFA
import org.antlr.v4.runtime.misc.BitSet
import org.antlr.v4.runtime.misc.Interval

/**
 * This implementation of [ANTLRErrorListener] can be used to identify
 * certain potential correctness and performance problems in grammars. "Reports"
 * are made by calling [Parser.notifyErrorListeners] with the appropriate
 * message.
 *
 *
 *  * **Ambiguities**: These are cases where more than one path through the
 * grammar can match the input.
 *  * **Weak context sensitivity**: These are cases where full-context
 * prediction resolved an SLL conflict to a unique alternative which equaled the
 * minimum alternative of the SLL conflict.
 *  * **Strong (forced) context sensitivity**: These are cases where the
 * full-context prediction resolved an SLL conflict to a unique alternative,
 * *and* the minimum alternative of the SLL conflict was found to not be
 * a truly viable alternative. Two-stage parsing cannot be used for inputs where
 * this situation occurs.
 *
 *
 * @author Sam Harwell
 */
class DiagnosticErrorListener
/**
     * Initializes a new instance of [DiagnosticErrorListener] which only
     * reports exact ambiguities.
     */
    @kotlin.jvm.JvmOverloads
    constructor(
        /**
         * When `true`, only exactly known ambiguities are reported.
         */
        protected val exactOnly: Boolean = true,
    ) : BaseErrorListener() {
        /**
         * Initializes a new instance of [DiagnosticErrorListener], specifying
         * whether all ambiguities or only exact ambiguities are reported.
         *
         * @param exactOnly `true` to report only exact ambiguities, otherwise
         * `false` to report all ambiguities.
         */
        fun reportAmbiguity(
            recognizer: Parser,
            dfa: DFA,
            startIndex: Int,
            stopIndex: Int,
            exact: Boolean,
            ambigAlts: BitSet?,
            configs: ATNConfigSet,
        ) {
            if (exactOnly && !exact) {
                return
            }

            val decision = getDecisionDescription(recognizer, dfa)
            val conflictingAlts: BitSet? = getConflictingAlts(ambigAlts, configs)
            val text: String? = recognizer.tokenStream.getText(Interval.of(startIndex, stopIndex))
            val message: String? = "reportAmbiguity d=$decision: ambigAlts=$conflictingAlts, input='$text'"
            recognizer.notifyErrorListeners(message)
        }

        fun reportAttemptingFullContext(
            recognizer: Parser,
            dfa: DFA,
            startIndex: Int,
            stopIndex: Int,
            conflictingAlts: BitSet?,
            configs: ATNConfigSet?,
        ) {
            val decision = getDecisionDescription(recognizer, dfa)
            val text: String? = recognizer.tokenStream.getText(Interval.of(startIndex, stopIndex))
            val message: String? = "reportAttemptingFullContext d=$decision, input='$text'"
            recognizer.notifyErrorListeners(message)
        }

        fun reportContextSensitivity(
            recognizer: Parser,
            dfa: DFA,
            startIndex: Int,
            stopIndex: Int,
            prediction: Int,
            configs: ATNConfigSet?,
        ) {
            val decision = getDecisionDescription(recognizer, dfa)
            val text: String? = recognizer.tokenStream.getText(Interval.of(startIndex, stopIndex))
            val message: String? = "reportContextSensitivity d=$decision, input='$text'"
            recognizer.notifyErrorListeners(message)
        }

        protected fun getDecisionDescription(
            recognizer: Parser,
            dfa: DFA,
        ): String {
            val decision: Int = dfa.decision
            val ruleIndex: Int = dfa.atnStartState.ruleIndex

            val ruleNames: Array<String?> = recognizer.ruleNames
            if (ruleIndex < 0 || ruleIndex >= ruleNames.size) {
                return decision.toString()
            }

            val ruleName = ruleNames[ruleIndex]
            if (ruleName == null || ruleName.isEmpty()) {
                return decision.toString()
            }

            return "$decision ($ruleName)"
        }

        /**
         * Computes the set of conflicting or ambiguous alternatives from a
         * configuration set, if that information was not already provided by the
         * parser.
         *
         * @param reportedAlts The set of conflicting or ambiguous alternatives, as
         * reported by the parser.
         * @param configs The conflicting or ambiguous configuration set.
         * @return Returns `reportedAlts` if it is not `null`, otherwise
         * returns the set of alternatives represented in `configs`.
         */
        protected fun getConflictingAlts(
            reportedAlts: BitSet?,
            configs: ATNConfigSet,
        ): BitSet? {
            if (reportedAlts != null) {
                return reportedAlts
            }

            val result: BitSet = BitSet()
            for (config in configs) {
                result.set(config.alt)
            }

            return result
        }
    }
