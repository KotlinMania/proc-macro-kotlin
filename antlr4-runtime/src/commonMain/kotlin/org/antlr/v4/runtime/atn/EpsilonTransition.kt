/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

class EpsilonTransition(target: ATNState, val outermostPrecedenceReturn: Int = -1) : Transition(target) {
    override val serializationType: Int
        get() = EPSILON

    override val isEpsilon: Boolean
        get() = true

    override fun matches(symbol: Int, minVocabSymbol: Int, maxVocabSymbol: Int): Boolean = false

    override fun toString(): String = if (outermostPrecedenceReturn == -1) "epsilon" else "epsilon>$outermostPrecedenceReturn"
}
