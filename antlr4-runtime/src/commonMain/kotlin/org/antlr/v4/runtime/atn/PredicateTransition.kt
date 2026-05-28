/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

class PredicateTransition(
    target: ATNState, val ruleIndex: Int, val predIndex: Int, val isCtxDependent: Boolean
) : AbstractPredicateTransition(target) {
    override val serializationType: Int
        get() = PREDICATE

    override val isEpsilon: Boolean
        get() = true

    override fun matches(symbol: Int, minVocabSymbol: Int, maxVocabSymbol: Int): Boolean = false

    val predicate: SemanticContext.Predicate
        get() = SemanticContext.Predicate(ruleIndex, predIndex, isCtxDependent)

    override fun toString(): String = "pred_$ruleIndex:$predIndex"
}
