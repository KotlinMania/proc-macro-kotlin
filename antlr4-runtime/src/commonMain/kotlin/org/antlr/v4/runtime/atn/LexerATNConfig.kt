/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.misc.AnyEqualityComparator
import org.antlr.v4.runtime.misc.MurmurHash

class LexerATNConfig : ATNConfig {
    private val lexerActionExecutor: LexerActionExecutor?
    private val passedThroughNonGreedyDecision: Boolean

    constructor(
        state: ATNState?,
        alt: Int,
        context: PredictionContext?,
    ) : super(state!!, alt, context, SemanticContext.Empty.Instance) {
        this.passedThroughNonGreedyDecision = false
        this.lexerActionExecutor = null
    }

    constructor(
        state: ATNState,
        alt: Int,
        context: PredictionContext?,
        lexerActionExecutor: LexerActionExecutor?,
    ) : super(state, alt, context, SemanticContext.Empty.Instance) {
        this.lexerActionExecutor = lexerActionExecutor
        this.passedThroughNonGreedyDecision = false
    }

    constructor(c: LexerATNConfig, state: ATNState) : super(c, state, c.context, c.semanticContext) {
        this.lexerActionExecutor = c.lexerActionExecutor
        this.passedThroughNonGreedyDecision = checkNonGreedyDecision(c, state)
    }

    constructor(
        c: LexerATNConfig,
        state: ATNState,
        lexerActionExecutor: LexerActionExecutor?,
    ) : super(c, state, c.context, c.semanticContext) {
        this.lexerActionExecutor = lexerActionExecutor
        this.passedThroughNonGreedyDecision = checkNonGreedyDecision(c, state)
    }

    constructor(
        c: LexerATNConfig,
        state: ATNState?,
        context: PredictionContext?,
    ) : super(c, state!!, context, c.semanticContext) {
        this.lexerActionExecutor = c.lexerActionExecutor
        this.passedThroughNonGreedyDecision = checkNonGreedyDecision(c, state)
    }

    fun getLexerActionExecutor(): LexerActionExecutor? = lexerActionExecutor

    fun hasPassedThroughNonGreedyDecision(): Boolean = passedThroughNonGreedyDecision

    override fun hashCode(): Int {
        var hashCode = MurmurHash.initialize(7)
        hashCode = MurmurHash.update(hashCode, state.stateNumber)
        hashCode = MurmurHash.update(hashCode, alt)
        hashCode = MurmurHash.update(hashCode, context)
        hashCode = MurmurHash.update(hashCode, semanticContext)
        hashCode = MurmurHash.update(hashCode, if (passedThroughNonGreedyDecision) 1 else 0)
        hashCode = MurmurHash.update(hashCode, lexerActionExecutor)
        hashCode = MurmurHash.finish(hashCode, 6)
        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LexerATNConfig) return false

        if (passedThroughNonGreedyDecision != other.passedThroughNonGreedyDecision) return false
        if (AnyEqualityComparator.INSTANCE.equals(lexerActionExecutor, other.lexerActionExecutor)) {
            return super.equals(other)
        }
        return false
    }

    companion object {
        private fun checkNonGreedyDecision(
            source: LexerATNConfig,
            target: ATNState,
        ): Boolean =
            source.passedThroughNonGreedyDecision ||
                (target is DecisionState && target.nonGreedy)
    }
}
