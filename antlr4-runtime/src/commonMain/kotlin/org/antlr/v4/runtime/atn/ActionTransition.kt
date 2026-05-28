/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

class ActionTransition(
    target: ATNState,
    val ruleIndex: Int,
    val actionIndex: Int,
    val isCtxDependent: Boolean,
) : Transition(target) {
    constructor(target: ATNState, ruleIndex: Int) : this(target, ruleIndex, -1, false)

    override val serializationType: Int
        get() = ACTION

    override val isEpsilon: Boolean
        get() = true

    override fun matches(
        symbol: Int,
        minVocabSymbol: Int,
        maxVocabSymbol: Int,
    ): Boolean = false

    override fun toString(): String = "action_$ruleIndex:$actionIndex"
}
