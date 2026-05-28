/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.misc.MurmurHash

/**
 * A tuple: (ATN state, predicted alt, syntactic, semantic context).
 * The syntactic context is a graph-structured stack node whose
 * path(s) to the root is the rule invocation(s) chain used to arrive
 * at the state. The semantic context is the tree of semantic predicates
 * encountered before reaching an ATN state.
 */
class ATNConfig {
    val state: ATNState
    val alt: Int
    var context: PredictionContext? = null

    var reachesIntoOuterContext: Int = 0

    val semanticContext: SemanticContext

    constructor(old: ATNConfig) {
        this.state = old.state
        this.alt = old.alt
        this.context = old.context
        this.semanticContext = old.semanticContext
        this.reachesIntoOuterContext = old.reachesIntoOuterContext
    }

    constructor(
        state: ATNState,
        alt: Int,
        context: PredictionContext?,
    ) : this(state, alt, context, SemanticContext.Empty.Instance)

    constructor(
        state: ATNState,
        alt: Int,
        context: PredictionContext?,
        semanticContext: SemanticContext,
    ) {
        this.state = state
        this.alt = alt
        this.context = context
        this.semanticContext = semanticContext
    }

    constructor(c: ATNConfig, state: ATNState) : this(c, state, c.context, c.semanticContext)

    constructor(
        c: ATNConfig,
        state: ATNState,
        semanticContext: SemanticContext,
    ) : this(c, state, c.context, semanticContext)

    constructor(
        c: ATNConfig,
        semanticContext: SemanticContext,
    ) : this(c, c.state, c.context, semanticContext)

    constructor(
        c: ATNConfig,
        state: ATNState,
        context: PredictionContext?,
    ) : this(c, state, context, c.semanticContext)

    constructor(
        c: ATNConfig,
        state: ATNState,
        context: PredictionContext?,
        semanticContext: SemanticContext,
    ) {
        this.state = state
        this.alt = c.alt
        this.context = context
        this.semanticContext = semanticContext
        this.reachesIntoOuterContext = c.reachesIntoOuterContext
    }

    val outerContextDepth: Int
        get() = reachesIntoOuterContext and SUPPRESS_PRECEDENCE_FILTER.inv()

    var isPrecedenceFilterSuppressed: Boolean
        get() = (reachesIntoOuterContext and SUPPRESS_PRECEDENCE_FILTER) != 0
        set(value) {
            if (value) {
                reachesIntoOuterContext = reachesIntoOuterContext or 0x40000000
            } else {
                reachesIntoOuterContext = reachesIntoOuterContext and SUPPRESS_PRECEDENCE_FILTER.inv()
            }
        }

    override fun equals(other: Any?): Boolean {
        if (other !is ATNConfig) return false
        return equals(other as ATNConfig)
    }

    fun equals(other: ATNConfig): Boolean {
        if (this === other) return true
        return state.stateNumber == other.state.stateNumber &&
            alt == other.alt &&
            context == other.context &&
            semanticContext == other.semanticContext &&
            isPrecedenceFilterSuppressed == other.isPrecedenceFilterSuppressed
    }

    override fun hashCode(): Int {
        var hashCode = MurmurHash.initialize(7)
        hashCode = MurmurHash.update(hashCode, state.stateNumber)
        hashCode = MurmurHash.update(hashCode, alt)
        hashCode = MurmurHash.update(hashCode, context)
        hashCode = MurmurHash.update(hashCode, semanticContext)
        hashCode = MurmurHash.finish(hashCode, 4)
        return hashCode
    }

    override fun toString(): String = toString(null, true)

    fun toString(
        recog: Recognizer<*, *>?,
        showAlt: Boolean,
    ): String {
        val buf = StringBuilder()
        buf.append('(')
        buf.append(state)
        if (showAlt) {
            buf.append(",")
            buf.append(alt)
        }
        if (context != null) {
            buf.append(",[")
            buf.append(context.toString())
            buf.append("]")
        }
        if (semanticContext != SemanticContext.Empty.Instance) {
            buf.append(",")
            buf.append(semanticContext)
        }
        if (outerContextDepth > 0) {
            buf.append(",up=").append(outerContextDepth)
        }
        buf.append(')')
        return buf.toString()
    }

    companion object {
        private const val SUPPRESS_PRECEDENCE_FILTER = 0x40000000
    }
}
