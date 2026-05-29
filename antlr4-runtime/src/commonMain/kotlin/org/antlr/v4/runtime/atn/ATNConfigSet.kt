/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.misc.AbstractEqualityComparator
import org.antlr.v4.runtime.misc.Array2DHashSet
import org.antlr.v4.runtime.misc.BitSet
import org.antlr.v4.runtime.misc.DoubleKeyMap

open class ATNConfigSet
    @kotlin.jvm.JvmOverloads
    constructor(
        val fullCtx: Boolean = true,
    ) : MutableSet<ATNConfig> {
        class ConfigHashSet : AbstractConfigHashSet(ConfigEqualityComparator.INSTANCE)

        class ConfigEqualityComparator private constructor() : AbstractEqualityComparator<ATNConfig>() {
            override fun hashCode(obj: ATNConfig): Int {
                var hashCode = 7
                hashCode = 31 * hashCode + obj.state.stateNumber
                hashCode = 31 * hashCode + obj.alt
                hashCode = 31 * hashCode + obj.semanticContext.hashCode()
                return hashCode
            }

            override fun equals(
                a: ATNConfig?,
                b: ATNConfig?,
            ): Boolean {
                if (a === b) return true
                if (a == null || b == null) return false
                return a.state.stateNumber == b.state.stateNumber && a.alt == b.alt && a.semanticContext == b.semanticContext
            }

            companion object {
                val INSTANCE = ConfigEqualityComparator()
            }
        }

        protected var readonly: Boolean = false

        var configLookup: AbstractConfigHashSet? = null

        val configs: MutableList<ATNConfig> = ArrayList(7)

        var uniqueAlt: Int = 0

        var conflictingAlts: BitSet? = null

        var hasSemanticContext: Boolean = false
        var dipsIntoOuterContext: Boolean = false

        private var cachedHashCode = -1

        init {
            configLookup = ConfigHashSet()
        }

        constructor(old: ATNConfigSet) : this(old.fullCtx) {
            addAll(old)
            this.uniqueAlt = old.uniqueAlt
            this.conflictingAlts = old.conflictingAlts
            this.hasSemanticContext = old.hasSemanticContext
            this.dipsIntoOuterContext = old.dipsIntoOuterContext
        }

        fun add(
            config: ATNConfig,
            mergeCache: DoubleKeyMap<PredictionContext, PredictionContext, PredictionContext>?,
        ): Boolean {
            check(!readonly) { "This set is readonly" }
            if (config.semanticContext !== SemanticContext.Empty.Instance) {
                hasSemanticContext = true
            }
            if (config.reachesIntoOuterContext > 0) {
                dipsIntoOuterContext = true
            }

            val existing = configLookup!!.getOrAdd(config)
            if (existing === config) {
                cachedHashCode = -1
                return true
            }

            val merged =
                PredictionContext.merge(
                    existing.context,
                    config.context,
                    fullCtx,
                    mergeCache,
                )

            if (existing.context == merged && existing.semanticContext == config.semanticContext) {
                return false
            }

            existing.context = merged
            existing.reachesIntoOuterContext =
                maxOf(
                    existing.reachesIntoOuterContext,
                    config.reachesIntoOuterContext,
                )

            cachedHashCode = -1
            return true
        }

        override fun add(element: ATNConfig): Boolean = add(element, null)

        fun getAlts(): BitSet {
            val alts = BitSet()
            for (config in configs) alts.set(config.alt)
            return alts
        }

        fun getAltLabels(): Map<Int, Set<ATNConfig>>? {
            val labels = mutableMapOf<Int, MutableSet<ATNConfig>>()
            for (config in configs) {
                labels.getOrPut(config.alt) { mutableSetOf() }.add(config)
            }
            return labels
        }


        fun getConflictingAltsResolved(): BitSet {
            if (conflictingAlts != null) return conflictingAlts!!
            val alts = BitSet()
            for (config in configs) alts.set(config.alt)
            return alts
        }

        val predicates: List<SemanticContext>
            get() = configs.filter { it.semanticContext !== SemanticContext.Empty.Instance }.map { it.semanticContext }

        fun get(i: Int): ATNConfig = configs[i]

        fun optimizeConfigs(interpreter: ATNSimulator) {
            check(!readonly) { "This set is readonly" }
            if (configLookup!!.isEmpty()) return
            for (config in configs) {
                config.context = interpreter.getCachedContext(config.context)
            }
        }

        override fun addAll(elements: Collection<ATNConfig>): Boolean {
            for (c in elements) add(c)
            return true
        }

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is ATNConfigSet) return false
            return configs == other.configs &&
                fullCtx == other.fullCtx &&
                uniqueAlt == other.uniqueAlt &&
                conflictingAlts == other.conflictingAlts &&
                hasSemanticContext == other.hasSemanticContext &&
                dipsIntoOuterContext == other.dipsIntoOuterContext
        }

        override fun hashCode(): Int {
            if (readonly && cachedHashCode == -1) {
                cachedHashCode = configs.hashCode()
            }
            return if (readonly) cachedHashCode else configs.hashCode()
        }

        override val size: Int
            get() = configs.size

        override fun isEmpty(): Boolean = configs.isEmpty()

        override fun contains(element: ATNConfig): Boolean {
            if (configLookup == null) throw UnsupportedOperationException("This method is not implemented for readonly sets.")
            return configLookup!!.contains(element)
        }

        fun containsFast(obj: ATNConfig): Boolean {
            if (configLookup == null) throw UnsupportedOperationException("This method is not implemented for readonly sets.")
            return configLookup!!.containsFast(obj)
        }

        override fun iterator(): MutableIterator<ATNConfig> = configs.iterator()

        override fun clear() {
            check(!readonly) { "This set is readonly" }
            configs.clear()
            cachedHashCode = -1
            configLookup!!.clear()
        }

        var readonlyFlag: Boolean
            get() = readonly
            set(value) {
                readonly = value
                configLookup = null
            }

        override fun toString(): String {
            val buf = StringBuilder()
            buf.append(configs.toString())
            if (hasSemanticContext) buf.append(",hasSemanticContext=").append(hasSemanticContext)
            if (uniqueAlt != ATN.INVALID_ALT_NUMBER) buf.append(",uniqueAlt=").append(uniqueAlt)
            if (conflictingAlts != null) buf.append(",conflictingAlts=").append(conflictingAlts)
            if (dipsIntoOuterContext) buf.append(",dipsIntoOuterContext")
            return buf.toString()
        }

        override fun remove(element: ATNConfig): Boolean = throw UnsupportedOperationException("")

        override fun containsAll(elements: Collection<ATNConfig>): Boolean = throw UnsupportedOperationException("")

        override fun retainAll(elements: Collection<ATNConfig>): Boolean = throw UnsupportedOperationException("")

        override fun removeAll(elements: Collection<ATNConfig>): Boolean = throw UnsupportedOperationException("")

        open class AbstractConfigHashSet(
            comparator: AbstractEqualityComparator<in ATNConfig>,
            initialCapacity: Int,
            initialBucketCapacity: Int,
        ) : Array2DHashSet<ATNConfig>(comparator, initialCapacity, initialBucketCapacity) {
            constructor(comparator: AbstractEqualityComparator<in ATNConfig>) : this(comparator, 16, 2)

            override fun asElementType(o: Any?): ATNConfig? = o as? ATNConfig

            override fun createBuckets(capacity: Int): Array<Array<ATNConfig?>?> = arrayOfNulls(capacity)

            override fun createBucket(capacity: Int): Array<ATNConfig?> = arrayOfNulls(capacity)
        }
    }
