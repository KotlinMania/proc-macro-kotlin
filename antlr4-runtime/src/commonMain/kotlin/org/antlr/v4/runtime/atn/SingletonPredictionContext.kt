/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

open class SingletonPredictionContext internal constructor(
    parent: PredictionContext?,
    returnState: Int,
) : PredictionContext(if (parent != null) calculateHashCode(parent, returnState) else calculateEmptyHashCode()) {
    val parent: PredictionContext?
    val returnState: Int

    init {
        assert(returnState != ATNState.INVALID_STATE_NUMBER)
        this.parent = parent
        this.returnState = returnState
    }

    override fun size(): Int = 1

    open fun getParent(index: Int): PredictionContext? {
        assert(index == 0)
        return parent
    }

    open fun getReturnState(index: Int): Int {
        assert(index == 0)
        return returnState
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        } else if (o !is SingletonPredictionContext) {
            return false
        }

        if (this.hashCode() !== o.hashCode()) {
            return false // can't be same if hash is different
        }

        val s = o
        return returnState == s.returnState &&
            (parent != null && parent.equals(s.parent))
    }

    override fun toString(): String? {
        val up = if (parent != null) parent.toString() else ""
        if (up.length === 0) {
            if (returnState == EMPTY_RETURN_STATE) {
                return "$"
            }
            return returnState.toString()
        }
        return returnState.toString() + " " + up
    }

    companion object {
        fun create(
            parent: PredictionContext?,
            returnState: Int,
        ): SingletonPredictionContext? {
            if (returnState == EMPTY_RETURN_STATE && parent == null) {
                // someone can pass in the bits of an array ctx that mean $
                return EmptyPredictionContext.Instance
            }
            return SingletonPredictionContext(parent, returnState)
        }
    }
}
