/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.dfa

import org.antlr.v4.runtime.Vocabulary
import org.antlr.v4.runtime.VocabularyImpl

/** A DFA walker that knows how to dump them to serialized strings.  */
open class DFASerializer(dfa: DFA, vocabulary: Vocabulary) {
    private val dfa: DFA

    private val vocabulary: Vocabulary

    @Deprecated
    @Deprecated("Use {@link #DFASerializer(DFA, Vocabulary)} instead.")
    constructor(dfa: DFA?, tokenNames: Array<String?>?) : this(dfa, VocabularyImpl.fromTokenNames(tokenNames))

    init {
        this.dfa = dfa
        this.vocabulary = vocabulary
    }
    fun toString(): String? {
        if (dfa.s0 == null) return null
        val buf: StringBuilder = StringBuilder()
        val states: List<DFAState?> = dfa.states
        for (s in states) {
            var n = 0
            if (s.edges != null) n = s.edges.size
            for (i in 0..<n) {
                val t: DFAState? = s.edges[i]
                if (t != null && t.stateNumber !== Int.MAX_VALUE) {
                    buf.append(getStateString(s))
                    val label = getEdgeLabel(i)
                    buf.append("-").append(label).append("->").append(getStateString(t)).append('\n')
                }
            }
        }

        val output = buf.toString()
        if (output.size === 0) return null
        //return Utils.sortLinesInString(output);
        return output
    }

    protected fun getEdgeLabel(i: Int): String {
        return vocabulary.getDisplayName(i - 1)
    }


    protected fun getStateString(s: DFAState): String? {
        val n: Int = s.stateNumber
        val baseStateStr = (if (s.isAcceptState) ":" else "") + "s" + n + (if (s.requiresFullContext) "^" else "")
        if (s.isAcceptState) {
            if (s.predicates != null) {
                return baseStateStr.toString() + "=>" + s.predicates.contentToString()
            } else {
                return baseStateStr.toString() + "=>" + s.prediction
            }
        } else {
            return baseStateStr
        }
    }
}
