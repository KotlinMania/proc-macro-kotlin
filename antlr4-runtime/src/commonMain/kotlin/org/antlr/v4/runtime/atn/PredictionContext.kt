/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.assert
import org.antlr.v4.runtime.misc.DoubleKeyMap
import org.antlr.v4.runtime.misc.MurmurHash

abstract class PredictionContext protected constructor(
    cachedHashCode: Int,
) {
    val id: Int = org.antlr.v4.runtime.atn.PredictionContext.Companion.globalNodeCount++

    /**
     * Stores the computed hash code of this [PredictionContext]. The hash
     * code is computed in parts to match the following reference algorithm.
     *
     * <pre>
     * private int referenceHashCode() {
     * int hash = [MurmurHash.initialize]([.INITIAL_HASH]);
     *
     * for (int i = 0; i &lt; [.size]; i++) {
     * hash = [MurmurHash.update](hash, [getParent][.getParent](i));
     * }
     *
     * for (int i = 0; i &lt; [.size]; i++) {
     * hash = [MurmurHash.update](hash, [getReturnState][.getReturnState](i));
     * }
     *
     * hash = [MurmurHash.finish](hash, 2 * [.size]);
     * return hash;
     * }
     </pre> *
     */
    val cachedHashCode: Int

    init {
        this.cachedHashCode = cachedHashCode
    }

    abstract fun size(): Int

    abstract fun getParent(index: Int): PredictionContext?

    abstract fun getReturnState(index: Int): Int

    open val isEmpty: Boolean
        /** This means only the [EmptyPredictionContext.Instance] (wildcard? not sure) context is in set.  */
        get() = this === EmptyPredictionContext.Instance

    fun hasEmptyPath(): Boolean {
        // since EMPTY_RETURN_STATE can only appear in the last position, we check last one
        return getReturnState(size() - 1) == org.antlr.v4.runtime.atn.PredictionContext.Companion.EMPTY_RETURN_STATE
    }

    override fun hashCode(): Int = cachedHashCode

    abstract override fun equals(other: Any?): Boolean

    fun toString(recog: Recognizer<*, *>?): String? {
        return toString()
        // 		return toString(recog, ParserRuleContext.EMPTY);
    }

    fun toStrings(
        recognizer: Recognizer<*, *>?,
        currentState: Int,
    ): Array<String?> = toStrings(recognizer, EmptyPredictionContext.Instance, currentState)

    // FROM SAM
    fun toStrings(
        recognizer: Recognizer<*, *>?,
        stop: PredictionContext?,
        currentState: Int,
    ): Array<String?> {
        val result: MutableList<String> = ArrayList()

        var perm = 0
        outer@ while (true) {
            var offset = 0
            var last = true
            var p = this
            var stateNumber = currentState
            val localBuffer: StringBuilder = StringBuilder()
            localBuffer.append("[")
            while (!p.isEmpty && p != stop) {
                var index = 0
                if (p.size() > 0) {
                    var bits = 1
                    while ((1 shl bits) < p.size()) {
                        bits++
                    }

                    val mask = (1 shl bits) - 1
                    index = (perm shr offset) and mask
                    last = last and (index >= p.size() - 1)
                    if (index >= p.size()) {
                        perm++
                        continue@outer
                    }
                    offset += bits
                }

                if (recognizer != null) {
                    if (localBuffer.length > 1) {
                        // first char is '[', if more than that this isn't the first rule
                        localBuffer.append(' ')
                    }

                    val atn: ATN = recognizer.atn
                    val s: ATNState = atn.states.get(stateNumber)
                    val ruleName: String? = recognizer.ruleNames?.get(s.ruleIndex)
                    localBuffer.append(ruleName)
                } else if (p.getReturnState(index) != org.antlr.v4.runtime.atn.PredictionContext.Companion.EMPTY_RETURN_STATE) {
                    if (!p.isEmpty) {
                        if (localBuffer.length > 1) {
                            // first char is '[', if more than that this isn't the first rule
                            localBuffer.append(' ')
                        }

                        localBuffer.append(p.getReturnState(index))
                    }
                }
                stateNumber = p.getReturnState(index)
                p = p.getParent(index)!!
            }
            localBuffer.append("]")
            result.add(localBuffer.toString())

            if (last) {
                break
            }
            perm++
        }
        return result.toList().toTypedArray()
    }

    companion object {
        /**
         * Represents `$` in an array in full context mode, when `$`
         * doesn't mean wildcard: `$ + x = [$,x]`. Here,
         * `$` = [.EMPTY_RETURN_STATE].
         */
        val EMPTY_RETURN_STATE: Int = Int.MAX_VALUE

        private const val INITIAL_HASH = 1

        private var globalNodeCount: Int = 0

        /** Convert a [RuleContext] tree to a [PredictionContext] graph.
         * Return [EmptyPredictionContext.Instance] if `outerContext` is empty or null.
         */
        fun fromRuleContext(
            atn: ATN,
            outerContext: RuleContext?,
        ): PredictionContext {
            var outerContext: RuleContext? = outerContext
            if (outerContext == null) outerContext = ParserRuleContext.EMPTY

            // if we are in RuleContext of start rule, s, then PredictionContext
            // is EMPTY. Nobody called us. (if we are empty, return empty)
            if (outerContext.parent == null || outerContext === ParserRuleContext.EMPTY) {
                return EmptyPredictionContext.Instance
            }

            // If we have a parent, convert it to a PredictionContext graph
            var parent: PredictionContext? = EmptyPredictionContext.Instance
            parent =
                org.antlr.v4.runtime.atn.PredictionContext.Companion
                    .fromRuleContext(atn, outerContext.parent as? RuleContext)

            val state: ATNState = atn.states.get(outerContext.invokingState)
            val transition: RuleTransition = state.transition(0) as RuleTransition
            return SingletonPredictionContext.create(parent, transition.followState.stateNumber)
        }

        internal fun calculateEmptyHashCode(): Int {
            var hash: Int = MurmurHash.initialize(org.antlr.v4.runtime.atn.PredictionContext.Companion.INITIAL_HASH)
            hash = MurmurHash.finish(hash, 0)
            return hash
        }

        internal fun calculateHashCode(
            parent: PredictionContext?,
            returnState: Int,
        ): Int {
            var hash: Int = MurmurHash.initialize(org.antlr.v4.runtime.atn.PredictionContext.Companion.INITIAL_HASH)
            hash = MurmurHash.update(hash, parent)
            hash = MurmurHash.update(hash, returnState)
            hash = MurmurHash.finish(hash, 2)
            return hash
        }

        internal fun calculateHashCode(
            parents: Array<PredictionContext?>,
            returnStates: IntArray,
        ): Int {
            var hash: Int = MurmurHash.initialize(org.antlr.v4.runtime.atn.PredictionContext.Companion.INITIAL_HASH)

            for (parent in parents) {
                hash = MurmurHash.update(hash, parent)
            }

            for (returnState in returnStates) {
                hash = MurmurHash.update(hash, returnState)
            }

            hash = MurmurHash.finish(hash, 2 * parents.size)
            return hash
        }

        // dispatch
        fun merge(
            a: PredictionContext?,
            b: PredictionContext?,
            rootIsWildcard: Boolean,
            mergeCache: DoubleKeyMap<PredictionContext, PredictionContext, PredictionContext>?,
        ): PredictionContext? {
            var a = a
            var b = b
            assert(
                a != null && b != null, // must be empty context, never null
            )

            // share same graph if both same
            if (a === b || a?.equals(b) == true) return a

            if (a is SingletonPredictionContext && b is SingletonPredictionContext) {
                return mergeSingletons(
                    a as SingletonPredictionContext,
                    b as SingletonPredictionContext,
                    rootIsWildcard,
                    mergeCache,
                )
            }

            // At least one of a or b is array
            // If one is $ and rootIsWildcard, return $ as * wildcard
            if (rootIsWildcard) {
                if (a is EmptyPredictionContext) return a
                if (b is EmptyPredictionContext) return b
            }

            // convert singleton so both are arrays to normalize
            if (a is SingletonPredictionContext) {
                a = ArrayPredictionContext(a as SingletonPredictionContext)
            }
            if (b is SingletonPredictionContext) {
                b = ArrayPredictionContext(b as SingletonPredictionContext)
            }
            return mergeArrays(
                a as ArrayPredictionContext,
                b as ArrayPredictionContext,
                rootIsWildcard,
                mergeCache,
            )
        }

        /**
         * Merge two [SingletonPredictionContext] instances.
         *
         *
         * Stack tops equal, parents merge is same; return left graph.<br></br>
         * <embed src="images/SingletonMerge_SameRootSamePar.svg" type="image/svg+xml"></embed>
         *
         *
         * Same stack top, parents differ; merge parents giving array node, then
         * remainders of those graphs. A new root node is created to point to the
         * merged parents.<br></br>
         * <embed src="images/SingletonMerge_SameRootDiffPar.svg" type="image/svg+xml"></embed>
         *
         *
         * Different stack tops pointing to same parent. Make array node for the
         * root where both element in the root point to the same (original)
         * parent.<br></br>
         * <embed src="images/SingletonMerge_DiffRootSamePar.svg" type="image/svg+xml"></embed>
         *
         *
         * Different stack tops pointing to different parents. Make array node for
         * the root where each element points to the corresponding original
         * parent.<br></br>
         * <embed src="images/SingletonMerge_DiffRootDiffPar.svg" type="image/svg+xml"></embed>
         *
         * @param a the first [SingletonPredictionContext]
         * @param b the second [SingletonPredictionContext]
         * @param rootIsWildcard `true` if this is a local-context merge,
         * otherwise false to indicate a full-context merge
         * @param mergeCache
         */
        fun mergeSingletons(
            a: SingletonPredictionContext,
            b: SingletonPredictionContext,
            rootIsWildcard: Boolean,
            mergeCache: DoubleKeyMap<PredictionContext, PredictionContext, PredictionContext>?,
        ): PredictionContext? {
            if (mergeCache != null) {
                var previous: PredictionContext? = mergeCache.get(a, b)
                if (previous != null) return previous
                previous = mergeCache.get(b, a)
                if (previous != null) return previous
            }

            val rootMerge: PredictionContext? =
                mergeRoot(a, b, rootIsWildcard)
            if (rootMerge != null) {
                if (mergeCache != null) mergeCache.put(a, b, rootMerge!!)
                return rootMerge
            }

            if (a.returnState == b.returnState) { // a == b
                val parent: PredictionContext? =
                    merge(
                        a.parent,
                        b.parent,
                        rootIsWildcard,
                        mergeCache,
                    )
                // if parent is same as existing a or b parent or reduced to a parent, return it
                if (parent === a.parent) return a // ax + bx = ax, if a=b

                if (parent === b.parent) return b // ax + bx = bx, if a=b

                // else: ax + ay = a'[x,y]
                // merge parents x and y, giving array node with x,y then remainders
                // of those graphs.  dup a, a' points at merged array
                // new joined parent so create new singleton pointing to it, a'
                val a_: PredictionContext? = SingletonPredictionContext.create(parent, a.returnState)
                if (mergeCache != null) mergeCache.put(a, b, a_!!)
                return a_
            } else { // a != b payloads differ
                // see if we can collapse parents due to $+x parents if local ctx
                var singleParent: PredictionContext? = null
                if (a === b || (a.parent != null && a.parent.equals(b.parent))) { // ax + bx = [a,b]x
                    singleParent = a.parent
                }
                if (singleParent != null) { // parents are same
                    // sort payloads and use same parent
                    val payloads = intArrayOf(a.returnState, b.returnState)
                    if (a.returnState > b.returnState) {
                        payloads[0] = b.returnState
                        payloads[1] = a.returnState
                    }
                    val parents = arrayOf<PredictionContext?>(singleParent, singleParent)
                    val a_: PredictionContext = ArrayPredictionContext(parents, payloads)
                    if (mergeCache != null) mergeCache.put(a, b, a_!!)
                    return a_
                }
                // parents differ and can't merge them. Just pack together
                // into array; can't merge.
                // ax + by = [ax,by]
                val payloads = intArrayOf(a.returnState, b.returnState)
                var parents = arrayOf<PredictionContext?>(a.parent, b.parent)
                if (a.returnState > b.returnState) { // sort by payload
                    payloads[0] = b.returnState
                    payloads[1] = a.returnState
                    parents = arrayOf<PredictionContext?>(b.parent, a.parent)
                }
                val a_: PredictionContext = ArrayPredictionContext(parents, payloads)
                if (mergeCache != null) mergeCache.put(a, b, a_!!)
                return a_
            }
        }

        /**
         * Handle case where at least one of `a` or `b` is
         * [EmptyPredictionContext.Instance]. In the following diagrams, the symbol `$` is used
         * to represent [EmptyPredictionContext.Instance].
         *
         * <h2>Local-Context Merges</h2>
         *
         *
         * These local-context merge operations are used when `rootIsWildcard`
         * is true.
         *
         *
         * [EmptyPredictionContext.Instance] is superset of any graph; return [EmptyPredictionContext.Instance].<br></br>
         * <embed src="images/LocalMerge_EmptyRoot.svg" type="image/svg+xml"></embed>
         *
         *
         * [EmptyPredictionContext.Instance] and anything is `#EMPTY`, so merged parent is
         * `#EMPTY`; return left graph.<br></br>
         * <embed src="images/LocalMerge_EmptyParent.svg" type="image/svg+xml"></embed>
         *
         *
         * Special case of last merge if local context.<br></br>
         * <embed src="images/LocalMerge_DiffRoots.svg" type="image/svg+xml"></embed>
         *
         * <h2>Full-Context Merges</h2>
         *
         *
         * These full-context merge operations are used when `rootIsWildcard`
         * is false.
         *
         *
         * <embed src="images/FullMerge_EmptyRoots.svg" type="image/svg+xml"></embed>
         *
         *
         * Must keep all contexts; [EmptyPredictionContext.Instance] in array is a special value (and
         * null parent).<br></br>
         * <embed src="images/FullMerge_EmptyRoot.svg" type="image/svg+xml"></embed>
         *
         *
         * <embed src="images/FullMerge_SameRoot.svg" type="image/svg+xml"></embed>
         *
         * @param a the first [SingletonPredictionContext]
         * @param b the second [SingletonPredictionContext]
         * @param rootIsWildcard `true` if this is a local-context merge,
         * otherwise false to indicate a full-context merge
         */
        fun mergeRoot(
            a: SingletonPredictionContext,
            b: SingletonPredictionContext,
            rootIsWildcard: Boolean,
        ): PredictionContext? {
            if (rootIsWildcard) {
                if (a === EmptyPredictionContext.Instance) return EmptyPredictionContext.Instance // * + b = *

                if (b === EmptyPredictionContext.Instance) return EmptyPredictionContext.Instance // a + * = *
            } else {
                if (a === EmptyPredictionContext.Instance && b === EmptyPredictionContext.Instance) return EmptyPredictionContext.Instance // $ + $ = $

                if (a === EmptyPredictionContext.Instance) { // $ + x = [x,$]
                    val payloads =
                        intArrayOf(
                            b.returnState,
                            org.antlr.v4.runtime.atn.PredictionContext.Companion.EMPTY_RETURN_STATE,
                        )
                    val parents = arrayOf<PredictionContext?>(b.parent, null)
                    val joined: PredictionContext =
                        ArrayPredictionContext(parents, payloads)
                    return joined
                }
                if (b === EmptyPredictionContext.Instance) { // x + $ = [x,$] ($ is always last if present)
                    val payloads =
                        intArrayOf(
                            a.returnState,
                            org.antlr.v4.runtime.atn.PredictionContext.Companion.EMPTY_RETURN_STATE,
                        )
                    val parents = arrayOf<PredictionContext?>(a.parent, null)
                    val joined: PredictionContext =
                        ArrayPredictionContext(parents, payloads)
                    return joined
                }
            }
            return null
        }

        /**
         * Merge two [ArrayPredictionContext] instances.
         *
         *
         * Different tops, different parents.<br></br>
         * <embed src="images/ArrayMerge_DiffTopDiffPar.svg" type="image/svg+xml"></embed>
         *
         *
         * Shared top, same parents.<br></br>
         * <embed src="images/ArrayMerge_ShareTopSamePar.svg" type="image/svg+xml"></embed>
         *
         *
         * Shared top, different parents.<br></br>
         * <embed src="images/ArrayMerge_ShareTopDiffPar.svg" type="image/svg+xml"></embed>
         *
         *
         * Shared top, all shared parents.<br></br>
         * <embed src="images/ArrayMerge_ShareTopSharePar.svg" type="image/svg+xml"></embed>
         *
         *
         * Equal tops, merge parents and reduce top to
         * [SingletonPredictionContext].<br></br>
         * <embed src="images/ArrayMerge_EqualTop.svg" type="image/svg+xml"></embed>
         */
        fun mergeArrays(
            a: ArrayPredictionContext,
            b: ArrayPredictionContext,
            rootIsWildcard: Boolean,
            mergeCache: DoubleKeyMap<PredictionContext, PredictionContext, PredictionContext>?,
        ): PredictionContext? {
            if (mergeCache != null) {
                var previous: PredictionContext? = mergeCache.get(a, b)
                if (previous != null) {
                    if (ParserATNSimulator.trace_atn_sim) println("mergeArrays a=" + a + ",b=" + b + " -> previous")
                    return previous
                }
                previous = mergeCache.get(b, a)
                if (previous != null) {
                    if (ParserATNSimulator.trace_atn_sim) println("mergeArrays a=" + a + ",b=" + b + " -> previous")
                    return previous
                }
            }

            // merge sorted payloads a + b => M
            var i = 0 // walks a
            var j = 0 // walks b
            var k = 0 // walks target M array

            var mergedReturnStates: IntArray? =
                IntArray(a.returnStates.size + b.returnStates.size)
            var mergedParents =
                arrayOfNulls<PredictionContext>(a.returnStates.size + b.returnStates.size)
            // walk and merge to yield mergedParents, mergedReturnStates
            while (i < a.returnStates.size && j < b.returnStates.size) {
                val a_parent: PredictionContext? = a.parents[i]
                val b_parent: PredictionContext? = b.parents[j]
                if (a.returnStates[i] == b.returnStates[j]) {
                    // same payload (stack tops are equal), must yield merged singleton
                    val payload: Int = a.returnStates[i]
                    // $+$ = $
                    val bothEmpty =
                        payload == org.antlr.v4.runtime.atn.PredictionContext.Companion.EMPTY_RETURN_STATE &&
                            a_parent == null &&
                            b_parent == null
                    val ax_ax =
                        (a_parent != null && b_parent != null) &&
                            a_parent.equals(b_parent) // ax+ax -> ax
                    if (bothEmpty || ax_ax) {
                        mergedParents[k] = a_parent // choose left
                        mergedReturnStates!![k] = payload
                    } else { // ax+ay -> a'[x,y]
                        val mergedParent: PredictionContext? =
                            merge(
                                a_parent,
                                b_parent,
                                rootIsWildcard,
                                mergeCache,
                            )
                        mergedParents[k] = mergedParent
                        mergedReturnStates!![k] = payload
                    }
                    i++ // hop over left one as usual
                    j++ // but also skip one in right side since we merge
                } else if (a.returnStates[i] < b.returnStates[j]) { // copy a[i] to M
                    mergedParents[k] = a_parent
                    mergedReturnStates!![k] = a.returnStates[i]
                    i++
                } else { // b > a, copy b[j] to M
                    mergedParents[k] = b_parent
                    mergedReturnStates!![k] = b.returnStates[j]
                    j++
                }
                k++
            }

            // copy over any payloads remaining in either array
            if (i < a.returnStates.size) {
                for (p in i..<a.returnStates.size) {
                    mergedParents[k] = a.parents[p]
                    mergedReturnStates!![k] = a.returnStates[p]
                    k++
                }
            } else {
                for (p in j..<b.returnStates.size) {
                    mergedParents[k] = b.parents[p]
                    mergedReturnStates!![k] = b.returnStates[p]
                    k++
                }
            }

            // trim merged if we combined a few that had same stack tops
            if (k < mergedParents.size) { // write index < last position; trim
                if (k == 1) { // for just one merged element, return singleton top
                    val a_: PredictionContext? =
                        SingletonPredictionContext.create(
                            mergedParents[0],
                            mergedReturnStates?.get(0) ?: 0,
                        )
                    if (mergeCache != null) mergeCache.put(a, b, a_!!)
                    return a_
                }
                mergedParents = mergedParents.copyOf(k)
                mergedReturnStates = mergedReturnStates!!.copyOf(k)
            }

            val M: PredictionContext =
                ArrayPredictionContext(mergedParents, mergedReturnStates!!)

            // if we created same array as a or b, return that instead
            // TODO: track whether this is possible above during merge sort for speed
            if (M.equals(a)) {
                if (mergeCache != null) mergeCache.put(a, b, a)
                if (ParserATNSimulator.trace_atn_sim) println("mergeArrays a=" + a + ",b=" + b + " -> a")
                return a
            }
            if (M.equals(b)) {
                if (mergeCache != null) mergeCache.put(a, b, b)
                if (ParserATNSimulator.trace_atn_sim) println("mergeArrays a=" + a + ",b=" + b + " -> b")
                return b
            }

            combineCommonParents(mergedParents)

            if (mergeCache != null) mergeCache.put(a, b, M)

            if (ParserATNSimulator.trace_atn_sim) {
                println("mergeArrays a=" + a + ",b=" + b + " -> " + M)
            }

            return M
        }

        /**
         * Make pass over all *M* `parents`; merge any `equals()`
         * ones.
         */
        fun combineCommonParents(parents: Array<PredictionContext?>) {
            val uniqueParents: MutableMap<PredictionContext?, PredictionContext?> =
                HashMap<PredictionContext?, PredictionContext?>()

            for (p in parents.indices) {
                val parent = parents[p]
                if (!uniqueParents.containsKey(parent)) { // don't replace
                    uniqueParents[parent] = parent
                }
            }

            for (p in parents.indices) {
                parents[p] = uniqueParents[parents[p]]
            }
        }

        fun toDOTString(context: PredictionContext?): String? {
            if (context == null) return ""
            val buf: StringBuilder = StringBuilder()
            buf.append("digraph G {\n")
            buf.append("rankdir=LR;\n")

            val nodes: List<PredictionContext?> =
                getAllContextNodes(context)
            (nodes as? MutableList<PredictionContext?>)?.sortWith(compareBy { it?.id ?: 0 })

            for (current in nodes) {
                if (current is SingletonPredictionContext) {
                    val s: String? = current.id.toString()
                    buf.append("  s").append(s)
                    var returnState: String? = current.getReturnState(0).toString()
                    if (current is EmptyPredictionContext) returnState = "$"
                    buf.append(" [label=\"").append(returnState).append("\"];\n")
                    continue
                }
                val arr: ArrayPredictionContext = current as ArrayPredictionContext
                buf.append("  s").append(arr.id)
                buf.append(" [shape=box, label=\"")
                buf.append("[")
                var first = true
                for (inv in arr.returnStates) {
                    if (!first) buf.append(", ")
                    if (inv == org.antlr.v4.runtime.atn.PredictionContext.Companion.EMPTY_RETURN_STATE) {
                        buf.append("$")
                    } else {
                        buf.append(inv)
                    }
                    first = false
                }
                buf.append("]")
                buf.append("\"];\n")
            }

            for (current in nodes) {
                if (current === EmptyPredictionContext.Instance) continue
                for (i in 0..<current!!.size()) {
                    if (current.getParent(i) == null) continue
                    val s: String? = current.id.toString()
                    buf.append("  s").append(s)
                    buf.append("->")
                    buf.append("s")
                    buf.append(current.getParent(i)!!.id)
                    if (current.size() > 1) {
                        buf.append(" [label=\"parent[" + i + "]\"];\n")
                    } else {
                        buf.append(";\n")
                    }
                }
            }

            buf.append("}\n")
            return buf.toString()
        }

        // From Sam
        fun getCachedContext(
            context: PredictionContext,
            contextCache: PredictionContextCache,
            visited: MutableMap<PredictionContext?, PredictionContext?>,
        ): PredictionContext? {
            if (context.isEmpty) {
                return context
            }

            var existing: PredictionContext? = visited.get(context)
            if (existing != null) {
                return existing
            }

            existing = contextCache.get(context)
            if (existing != null) {
                visited[context] = existing
                return existing
            }

            var changed = false
            var parents = arrayOfNulls<PredictionContext>(context.size())
            var i = 0
            while (i < parents.size) {
                val parent: PredictionContext? =
                    getCachedContext(
                        context.getParent(i)!!,
                        contextCache,
                        visited,
                    )
                if (changed || parent != context.getParent(i)) {
                    if (!changed) {
                        parents = arrayOfNulls<PredictionContext>(context.size())
                        for (j in 0..<context.size()) {
                            parents[j] = context.getParent(j)
                        }

                        changed = true
                    }

                    parents[i] = parent
                }
                i++
            }

            if (!changed) {
                contextCache.add(context)
                visited[context] = context
                return context
            }

            val updated: PredictionContext?
            if (parents.size == 0) {
                updated = EmptyPredictionContext.Instance
            } else if (parents.size == 1) {
                updated = SingletonPredictionContext.create(parents[0], context.getReturnState(0))
            } else {
                val arrayPredictionContext: ArrayPredictionContext = context as ArrayPredictionContext
                updated = ArrayPredictionContext(parents, arrayPredictionContext.returnStates)
            }

            contextCache.add(updated)
            visited[updated] = updated
            visited.put(context, updated)

            return updated
        }

        // 	// extra structures, but cut/paste/morphed works, so leave it.
        // 	// seems to do a breadth-first walk
        // 	public static List<PredictionContext> getAllNodes(PredictionContext context) {
        // 		Map<PredictionContext, PredictionContext> visited =
        // 			new IdentityHashMap<PredictionContext, PredictionContext>();
        // 		Deque<PredictionContext> workList = new ArrayDeque<PredictionContext>();
        // 		workList.add(context);
        // 		visited[context] = context;
        // 		List<PredictionContext> nodes = new ArrayList<PredictionContext>();
        // 		while (!workList.isEmpty) {
        // 			PredictionContext current = workList.pop();
        // 			nodes.add(current);
        // 			for (int i = 0; i < current.size; i++) {
        // 				PredictionContext parent = current.getParent(i);
        // 				if ( parent!=null && visited.put(parent, parent) == null) {
        // 					workList.push(parent);
        // 				}
        // 			}
        // 		}
        // 		return nodes;
        // 	}
        // ter's recursive version of Sam's getAllNodes()
        fun getAllContextNodes(context: PredictionContext?): List<PredictionContext?> {
            val nodes: MutableList<PredictionContext?> = ArrayList<PredictionContext?>()
            val visited: MutableMap<PredictionContext?, PredictionContext?> =
                HashMap<PredictionContext?, PredictionContext?>()
            org.antlr.v4.runtime.atn.PredictionContext.Companion
                .getAllContextNodes_(context, nodes, visited)
            return nodes
        }

        fun getAllContextNodes_(
            context: PredictionContext?,
            nodes: MutableList<PredictionContext?>,
            visited: MutableMap<PredictionContext?, PredictionContext?>,
        ) {
            if (context == null || visited.containsKey(context)) return
            visited[context] = context
            nodes.add(context)
            for (i in 0..<context.size()) {
                getAllContextNodes_(
                    context.getParent(i)!!,
                    nodes,
                    visited,
                )
            }
        }
    }
}
