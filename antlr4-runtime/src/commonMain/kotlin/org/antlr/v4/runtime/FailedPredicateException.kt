/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

import org.antlr.v4.runtime.atn.ATNState
import org.antlr.v4.runtime.atn.AbstractPredicateTransition
import org.antlr.v4.runtime.atn.PredicateTransition

/** A semantic predicate failed during validation.  Validation of predicates
 * occurs when normally parsing the alternative just like matching a token.
 * Disambiguating predicate evaluation occurs when we test a predicate during
 * prediction.
 */
class FailedPredicateException(
    recognizer: Parser,
    predicate: String?,
    message: String?,
) : RecognitionException(
        formatMessage(predicate, message),
        recognizer,
        recognizer.inputStream,
        recognizer._ctx,
    ) {
    val ruleIndex: Int
    val predIndex: Int
    val predicate: String?

    constructor(recognizer: Parser) : this(recognizer, null)

    constructor(recognizer: Parser, predicate: String?) : this(recognizer, predicate, null)

    init {
        val s: ATNState =
            recognizer.interpreter!!
                .atn.states
                .get(recognizer.state)

        val trans: AbstractPredicateTransition = s.transition(0) as AbstractPredicateTransition
        if (trans is PredicateTransition) {
            this.ruleIndex = (trans as PredicateTransition).ruleIndex
            this.predIndex = (trans as PredicateTransition).predIndex
        } else {
            this.ruleIndex = 0
            this.predIndex = 0
        }

        this.predicate = predicate
        this.offendingToken = recognizer.currentToken
    }

    companion object {
        private fun formatMessage(
            predicate: String?,
            message: String?,
        ): String? {
            if (message != null) {
                return message
            }

            return "failed predicate: {$predicate}?"
        }
    }
}
