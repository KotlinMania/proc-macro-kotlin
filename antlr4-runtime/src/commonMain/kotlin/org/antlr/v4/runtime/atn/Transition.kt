/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.misc.IntervalSet
import kotlin.reflect.KClass

abstract class Transition protected constructor(val target: ATNState) {
    abstract val serializationType: Int

    open val isEpsilon: Boolean
        get() = false

    open fun label(): IntervalSet? = null

    abstract fun matches(symbol: Int, minVocabSymbol: Int, maxVocabSymbol: Int): Boolean

    companion object {
        const val EPSILON: Int = 1
        const val RANGE: Int = 2
        const val RULE: Int = 3
        const val PREDICATE: Int = 4
        const val ATOM: Int = 5
        const val ACTION: Int = 6
        const val SET: Int = 7
        const val NOT_SET: Int = 8
        const val WILDCARD: Int = 9
        const val PRECEDENCE: Int = 10

        val serializationNames: List<String> = listOf(
            "INVALID", "EPSILON", "RANGE", "RULE", "PREDICATE",
            "ATOM", "ACTION", "SET", "NOT_SET", "WILDCARD", "PRECEDENCE"
        )

        val serializationTypes: Map<KClass<out Transition>, Int> = mapOf(
            EpsilonTransition::class to EPSILON,
            RangeTransition::class to RANGE,
            RuleTransition::class to RULE,
            PredicateTransition::class to PREDICATE,
            AtomTransition::class to ATOM,
            ActionTransition::class to ACTION,
            SetTransition::class to SET,
            NotSetTransition::class to NOT_SET,
            WildcardTransition::class to WILDCARD,
            PrecedencePredicateTransition::class to PRECEDENCE
        )
    }
}
