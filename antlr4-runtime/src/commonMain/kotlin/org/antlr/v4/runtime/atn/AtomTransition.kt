/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.misc.IntervalSet

class AtomTransition(
    target: ATNState,
    val label: Int,
) : Transition(target) {
    override val serializationType: Int
        get() = ATOM

    override fun label(): IntervalSet = IntervalSet.of(label)

    override fun matches(
        symbol: Int,
        minVocabSymbol: Int,
        maxVocabSymbol: Int,
    ): Boolean = label == symbol

    override fun toString(): String = label.toString()
}
