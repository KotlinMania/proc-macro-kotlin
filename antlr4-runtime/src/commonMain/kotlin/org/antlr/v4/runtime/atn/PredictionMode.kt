/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.misc.AbstractEqualityComparator
import org.antlr.v4.runtime.misc.BitSet
import org.antlr.v4.runtime.misc.FlexibleHashMap
import org.antlr.v4.runtime.misc.MurmurHash

enum class PredictionMode {
    SLL,
    LL,
    LL_EXACT_AMBIG_DETECTION,
    ;

    internal class AltAndContextMap : FlexibleHashMap<ATNConfig, BitSet>(AltAndContextConfigEqualityComparator.INSTANCE)

    private class AltAndContextConfigEqualityComparator : AbstractEqualityComparator<ATNConfig>() {
        override fun hashCode(o: ATNConfig): Int {
            var hashCode = MurmurHash.initialize(7)
            hashCode = MurmurHash.update(hashCode, o.state.stateNumber)
            hashCode = MurmurHash.update(hashCode, o.context)
            hashCode = MurmurHash.finish(hashCode, 2)
            return hashCode
        }

        override fun equals(
            a: ATNConfig?,
            b: ATNConfig?,
        ): Boolean {
            if (a === b) return true
            if (a == null || b == null) return false
            return a.state.stateNumber == b.state.stateNumber && a.context == b.context
        }

        companion object {
            val INSTANCE = AltAndContextConfigEqualityComparator()
        }
    }

    companion object {
        fun hasSLLConflictTerminatingPrediction(
            mode: PredictionMode,
            configs: ATNConfigSet,
        ): Boolean {
            if (configs.hasSemanticContext) return false
            val altsets = getConflictingAltSubsets(configs)
            return if (mode == SLL) hasConflictingAltSet(altsets) else hasConflictingAltSet(altsets) && !allSubsetsConflict(altsets)
        }

        fun evalSemanticContext(
            predPredictions: Array<DFAState.PredPrediction>,
            parser: Recognizer<*, *>,
            outerContext: RuleContext?,
            complete: Boolean,
        ): Int {
            val predictions = BitSet()
            for (pair in predPredictions) {
                if (pair.pred is SemanticContext.Predicate) {
                    val pred = pair.pred as SemanticContext.Predicate
                    var evalResult = false
                    if (pred.ruleIndex == -1 || pred.isCtxDependent) {
                        if (complete) {
                            evalResult = parser.sempred(outerContext, pred.ruleIndex, pred.predIndex)
                        } else {
                            continue
                        }
                    } else {
                        evalResult = parser.sempred(outerContext, pred.ruleIndex, pred.predIndex)
                    }
                    if (evalResult && pair.alt != ATN.INVALID_ALT_NUMBER) predictions.set(pair.alt)
                } else if (pair.pred is SemanticContext.PrecedencePredicate) {
                    val pred = pair.pred as SemanticContext.PrecedencePredicate
                    val evalResult = parser.precpred(outerContext, pred.precedence)
                    if (evalResult && pair.alt != ATN.INVALID_ALT_NUMBER) predictions.set(pair.alt)
                }
            }
            if (predictions.nextSetBit(0) < 0) return ATN.INVALID_ALT_NUMBER
            return predictions.nextSetBit(0)
        }

        fun resolvesToJustOneViableAlt(altsets: Collection<BitSet>): Int =
            if (allSubsetsConflict(altsets)) getSingleViableAlt(altsets) else getUniqueAlt(altsets)

        fun allSubsetsConflict(altsets: Collection<BitSet>): Boolean = !hasNonConflictingAltSet(altsets)

        fun hasNonConflictingAltSet(altsets: Collection<BitSet>): Boolean {
            for (alts in altsets) {
                if (alts.cardinality() == 1) return true
            }
            return false
        }

        fun hasConflictingAltSet(altsets: Collection<BitSet>): Boolean {
            for (alts in altsets) {
                if (alts.cardinality() > 1) return true
            }
            return false
        }

        fun allSubsetsEqual(altsets: Collection<BitSet>): Boolean {
            val it = altsets.iterator()
            val first = it.next()
            while (it.hasNext()) {
                if (it.next() != first) return false
            }
            return true
        }

        fun getUniqueAlt(altsets: Collection<BitSet>): Int {
            val all = getAlts(altsets)
            return if (all.cardinality() == 1) all.nextSetBit(0) else ATN.INVALID_ALT_NUMBER
        }

        fun getAlts(altsets: Collection<BitSet>): BitSet {
            val all = BitSet()
            for (alts in altsets) all.or(alts)
            return all
        }

        fun getAlts(configs: ATNConfigSet): BitSet {
            val alts = BitSet()
            for (config in configs) alts.set(config.alt)
            return alts
        }

        fun getConflictingAltSubsets(configs: ATNConfigSet): Collection<BitSet> {
            val configToAlts = AltAndContextMap()
            for (c in configs) {
                var alts = configToAlts.get(c)
                if (alts == null) {
                    alts = BitSet()
                    configToAlts.put(c, alts)
                }
                alts.set(c.alt)
            }
            return configToAlts.values
        }

        fun getStateToAltMap(configs: ATNConfigSet): Map<ATNState, BitSet> {
            val m = mutableMapOf<ATNState, BitSet>()
            for (c in configs) {
                var alts = m[c.state]
                if (alts == null) {
                    alts = BitSet()
                    m[c.state] = alts
                }
                alts.set(c.alt)
            }
            return m
        }

        fun hasStateAssociatedWithOneAlt(configs: ATNConfigSet): Boolean {
            val x = getStateToAltMap(configs)
            for (alts in x.values) {
                if (alts.cardinality() == 1) return true
            }
            return false
        }

        fun getSingleViableAlt(altsets: Collection<BitSet>): Int {
            val viableAlts = BitSet()
            for (alts in altsets) {
                val minAlt = alts.nextSetBit(0)
                viableAlts.set(minAlt)
                if (viableAlts.cardinality() > 1) return ATN.INVALID_ALT_NUMBER
            }
            return viableAlts.nextSetBit(0)
        }
    }
}
