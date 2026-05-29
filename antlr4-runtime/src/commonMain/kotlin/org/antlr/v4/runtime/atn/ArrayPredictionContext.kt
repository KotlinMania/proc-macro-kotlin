/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.assert

class ArrayPredictionContext(
    parents: Array<PredictionContext?>,
    returnStates: IntArray,
) : PredictionContext(calculateHashCode(parents, returnStates)) {
    /** Parent can be null only if full ctx mode and we make an array
     * from [.EMPTY] and non-empty. We merge [.EMPTY] by using null parent and
     * returnState == [.EMPTY_RETURN_STATE].
     */
    val parents: Array<PredictionContext?>

    /** Sorted for merge, no duplicates; if present,
     * [.EMPTY_RETURN_STATE] is always last.
     */
    val returnStates: IntArray

    constructor(a: SingletonPredictionContext) : this(arrayOf<PredictionContext?>(a.parent), intArrayOf(a.returnState))

    init {
        assert(parents.size > 0)
        assert(returnStates.size > 0)
        this.parents = parents
        this.returnStates = returnStates
    }

    override val isEmpty: Boolean
        get() = // since EMPTY_RETURN_STATE can only appear in the last position, we
            // don't need to verify that size==1
            returnStates[0] == EMPTY_RETURN_STATE

    override fun size(): Int = returnStates.size

    override fun getParent(index: Int): PredictionContext? = parents[index]

    override fun getReturnState(index: Int): Int = returnStates[index]

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        } else if (o !is ArrayPredictionContext) {
            return false
        }

        if (this.hashCode() != o.hashCode()) {
            return false // can't be same if hash is different
        }

        return returnStates.contentEquals(o.returnStates) &&
            parents.contentEquals(o.parents)
    }

    override fun toString(): String {
        if (this.isEmpty) return "[]"
        val buf: StringBuilder = StringBuilder()
        buf.append("[")
        for (i in returnStates.indices) {
            if (i > 0) buf.append(", ")
            if (returnStates[i] == EMPTY_RETURN_STATE) {
                buf.append("$")
                continue
            }
            buf.append(returnStates[i])
            if (parents[i] != null) {
                buf.append(' ')
                buf.append(parents[i].toString())
            } else {
                buf.append("null")
            }
        }
        buf.append("]")
        return buf.toString()
    }
}
