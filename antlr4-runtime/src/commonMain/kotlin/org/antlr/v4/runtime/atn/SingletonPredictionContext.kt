/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.assert

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

    override fun getParent(index: Int): PredictionContext? {
        assert(index == 0)
        return parent
    }

    override fun getReturnState(index: Int): Int {
        assert(index == 0)
        return returnState
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        } else if (o !is SingletonPredictionContext) {
            return false
        }

        if (this.hashCode() != o.hashCode()) {
            return false // can't be same if hash is different
        }

        return returnState == o.returnState &&
            (parent != null && parent == o.parent)
    }

    override fun toString(): String {
        val up = parent?.toString() ?: ""
        if (up.isEmpty()) {
            if (returnState == EMPTY_RETURN_STATE) {
                return "$"
            }
            return returnState.toString()
        }
        return "$returnState $up"
    }

    companion object {
        fun create(
            parent: PredictionContext?,
            returnState: Int,
        ): SingletonPredictionContext {
            if (returnState == EMPTY_RETURN_STATE && parent == null) {
                return EmptyPredictionContext.Instance
            }
            return SingletonPredictionContext(parent, returnState)
        }
    }
}
