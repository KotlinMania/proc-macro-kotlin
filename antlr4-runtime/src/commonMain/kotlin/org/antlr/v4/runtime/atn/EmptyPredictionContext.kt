/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

class EmptyPredictionContext private constructor() : SingletonPredictionContext(null, EMPTY_RETURN_STATE) {
    override val isEmpty: Boolean
        get() = true
    override fun size(): Int {
        return 1
    }
    override fun getParent(index: Int): PredictionContext? {
        return null
    }
    override fun getReturnState(index: Int): Int {
        return returnState
    }
    override fun equals(o: Any?): Boolean {
        return this === o
    }
    override fun toString(): String {
        return "$"
    }

    companion object {
        /**
         * Represents `$` in local context prediction, which means wildcard.
         * `*+x = *`.
         */
        val Instance: EmptyPredictionContext = org.antlr.v4.runtime.atn.EmptyPredictionContext()
    }
}
