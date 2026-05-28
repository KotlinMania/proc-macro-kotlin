/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.dfa

import org.antlr.v4.runtime.atn.ATNConfigSet
import org.antlr.v4.runtime.atn.LexerActionExecutor
import org.antlr.v4.runtime.atn.SemanticContext
import org.antlr.v4.runtime.misc.MurmurHash

class DFAState {
    var stateNumber: Int = -1

    var configs: ATNConfigSet = ATNConfigSet()

    var edges: Array<DFAState?>? = null

    var isAcceptState: Boolean = false

    var prediction: Int = 0

    var lexerActionExecutor: LexerActionExecutor? = null

    var requiresFullContext: Boolean = false

    var predicates: Array<PredPrediction>? = null

    class PredPrediction(
        val pred: SemanticContext,
        val alt: Int,
    ) {
        override fun toString(): String = "($pred, $alt)"
    }

    constructor()

    constructor(stateNumber: Int) {
        this.stateNumber = stateNumber
    }

    constructor(configs: ATNConfigSet) {
        this.configs = configs
    }

    val altSet: Set<Int>?
        get() {
            val alts = mutableSetOf<Int>()
            if (configs != null) {
                for (c in configs) alts.add(c.alt)
            }
            if (alts.isEmpty()) return null
            return alts
        }

    override fun hashCode(): Int {
        var hash = MurmurHash.initialize(7)
        hash = MurmurHash.update(hash, configs?.hashCode() ?: 0)
        hash = MurmurHash.finish(hash, 1)
        return hash
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DFAState) return false
        return configs == other.configs
    }

    override fun toString(): String {
        val buf = StringBuilder()
        buf.append(stateNumber).append(":").append(configs)
        if (isAcceptState) {
            buf.append("=>")
            if (predicates != null) {
                buf.append(predicates!!.contentToString())
            } else {
                buf.append(prediction)
            }
        }
        return buf.toString()
    }
}
