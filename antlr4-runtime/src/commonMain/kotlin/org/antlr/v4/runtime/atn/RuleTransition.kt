/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

class RuleTransition(
    ruleStart: ATNState, val ruleIndex: Int, val precedence: Int, val followState: ATNState?
) : Transition(ruleStart) {
    override val serializationType: Int
        get() = RULE

    override val isEpsilon: Boolean
        get() = true

    override fun matches(symbol: Int, minVocabSymbol: Int, maxVocabSymbol: Int): Boolean = false
}
