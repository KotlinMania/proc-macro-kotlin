/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

class PredictionContextCache {
    protected val cache: MutableMap<PredictionContext, PredictionContext> = HashMap()

    fun add(ctx: PredictionContext?): PredictionContext? {
        if (ctx === EmptyPredictionContext.Instance) return EmptyPredictionContext.Instance
        val existing = cache[ctx]
        if (existing != null) return existing
        if (ctx != null) cache[ctx] = ctx
        return ctx
    }

    fun get(ctx: PredictionContext?): PredictionContext? = cache[ctx]

    val size: Int get() = cache.size
}
