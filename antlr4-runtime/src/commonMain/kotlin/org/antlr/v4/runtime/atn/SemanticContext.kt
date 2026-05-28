/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.misc.MurmurHash
import org.antlr.v4.runtime.misc.Utils

abstract class SemanticContext {
    abstract fun eval(parser: Recognizer<*, *>?, parserCallStack: RuleContext?): Boolean

    open fun evalPrecedence(parser: Recognizer<*, *>?, parserCallStack: RuleContext?): SemanticContext? {
        return this
    }

    class Empty : SemanticContext() {
        override fun eval(parser: Recognizer<*, *>?, parserCallStack: RuleContext?): Boolean = false

        companion object {
            val Instance: Empty = Empty()
        }
    }

    class Predicate : SemanticContext {
        val ruleIndex: Int
        val predIndex: Int
        val isCtxDependent: Boolean

        internal constructor() {
            this.ruleIndex = -1
            this.predIndex = -1
            this.isCtxDependent = false
        }

        constructor(ruleIndex: Int, predIndex: Int, isCtxDependent: Boolean) {
            this.ruleIndex = ruleIndex
            this.predIndex = predIndex
            this.isCtxDependent = isCtxDependent
        }

        override fun eval(parser: Recognizer<*, *>, parserCallStack: RuleContext?): Boolean {
            val localctx = if (isCtxDependent) parserCallStack else null
            return parser.sempred(localctx, ruleIndex, predIndex)
        }

        override fun hashCode(): Int {
            var hashCode = MurmurHash.initialize()
            hashCode = MurmurHash.update(hashCode, ruleIndex)
            hashCode = MurmurHash.update(hashCode, predIndex)
            hashCode = MurmurHash.update(hashCode, if (isCtxDependent) 1 else 0)
            hashCode = MurmurHash.finish(hashCode, 3)
            return hashCode
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Predicate) return false
            if (this === other) return true
            return ruleIndex == other.ruleIndex && predIndex == other.predIndex && isCtxDependent == other.isCtxDependent
        }

        override fun toString(): String = "{$ruleIndex:$predIndex}?"
    }

    class PrecedencePredicate : SemanticContext, Comparable<PrecedencePredicate> {
        val precedence: Int

        internal constructor() {
            this.precedence = 0
        }

        constructor(precedence: Int) {
            this.precedence = precedence
        }

        override fun eval(parser: Recognizer<*, *>, parserCallStack: RuleContext?): Boolean {
            return parser.precpred(parserCallStack, precedence)
        }

        override fun evalPrecedence(parser: Recognizer<*, *>?, parserCallStack: RuleContext?): SemanticContext? {
            return if (parser.precpred(parserCallStack, precedence)) Empty.Instance else null
        }

        override fun compareTo(other: PrecedencePredicate): Int = precedence.compareTo(other.precedence)

        override fun hashCode(): Int = 31 * precedence + 1

        override fun equals(other: Any?): Boolean {
            if (other !is PrecedencePredicate) return false
            return precedence == other.precedence
        }

        override fun toString(): String = "{$precedence>=}?"
    }

    abstract class Operator : SemanticContext() {
        abstract val operands: Array<SemanticContext>
        abstract fun getOperands(): Collection<SemanticContext>
    }

    class AND : Operator {
        override val operands: Array<SemanticContext>

        constructor(a: SemanticContext, b: SemanticContext) {
            val operands = mutableListOf<SemanticContext>()
            if (a is AND) operands.addAll(a.operands.toList()) else operands.add(a)
            if (b is AND) operands.addAll(b.operands.toList()) else operands.add(b)

            val precedencePredicates = filterPrecedencePredicates(operands)
            if (precedencePredicates.isNotEmpty()) {
                val reduced = precedencePredicates.min()
                operands.removeAll(precedencePredicates)
                operands.add(0, reduced)
            }

            this.operands = operands.toTypedArray()
        }

        override fun getOperands(): Collection<SemanticContext> = operands.toList()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AND) return false
            return operands.contentEquals(other.operands)
        }

        override fun hashCode(): Int = MurmurHash.hashCode(operands, AND::class.hashCode())

        override fun eval(parser: Recognizer<*, *>?, parserCallStack: RuleContext?): Boolean {
            for (opnd in operands) {
                if (!opnd.eval(parser, parserCallStack)) return false
            }
            return true
        }

        override fun evalPrecedence(parser: Recognizer<*, *>?, parserCallStack: RuleContext?): SemanticContext? {
            var differs = false
            val operands = mutableListOf<SemanticContext>()
            for (context in this.operands) {
                val evaluated = context.evalPrecedence(parser, parserCallStack)
                differs = differs or (evaluated !== context)
                if (evaluated == null) return null
                if (evaluated !== Empty.Instance) operands.add(evaluated)
            }

            if (!differs) return this

            if (operands.isEmpty) return Empty.Instance

            var result: SemanticContext = operands[0]
            for (i in 1 until operands.size) {
                result = and(result, operands[i])!!
            }
            return result
        }

        override fun toString(): String = Utils.join(operands.toList().iterator(), "&&")
    }

    class OR : Operator {
        override val operands: Array<SemanticContext>

        constructor(a: SemanticContext, b: SemanticContext) {
            val operands = mutableListOf<SemanticContext>()
            if (a is OR) operands.addAll(a.operands.toList()) else operands.add(a)
            if (b is OR) operands.addAll(b.operands.toList()) else operands.add(b)

            val precedencePredicates = filterPrecedencePredicates(operands)
            if (precedencePredicates.isNotEmpty()) {
                val reduced = precedencePredicates.max()
                operands.removeAll(precedencePredicates)
                operands.add(reduced)
            }

            this.operands = operands.toTypedArray()
        }

        override fun getOperands(): Collection<SemanticContext> = operands.toList()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is OR) return false
            return operands.contentEquals(other.operands)
        }

        override fun hashCode(): Int = MurmurHash.hashCode(operands, OR::class.hashCode())

        override fun eval(parser: Recognizer<*, *>?, parserCallStack: RuleContext?): Boolean {
            for (opnd in operands) {
                if (opnd.eval(parser, parserCallStack)) return true
            }
            return false
        }

        override fun evalPrecedence(parser: Recognizer<*, *>?, parserCallStack: RuleContext?): SemanticContext? {
            var differs = false
            val operands = mutableListOf<SemanticContext>()
            for (context in this.operands) {
                val evaluated = context.evalPrecedence(parser, parserCallStack)
                differs = differs or (evaluated !== context)
                if (evaluated === Empty.Instance) return Empty.Instance
                if (evaluated != null) operands.add(evaluated)
            }

            if (!differs) return this

            if (operands.isEmpty) return null

            var result: SemanticContext = operands[0]
            for (i in 1 until operands.size) {
                result = or(result, operands[i])!!
            }
            return result
        }

        override fun toString(): String = Utils.join(operands.toList().iterator(), "||")
    }

    companion object {
        fun and(a: SemanticContext?, b: SemanticContext?): SemanticContext? {
            if (a == null || a === Empty.Instance) return b
            if (b == null || b === Empty.Instance) return a
            val result = AND(a, b)
            if (result.operands.size == 1) return result.operands[0]
            return result
        }

        fun or(a: SemanticContext?, b: SemanticContext?): SemanticContext? {
            if (a == null) return b
            if (b == null) return a
            if (a === Empty.Instance || b === Empty.Instance) return Empty.Instance
            val result = OR(a, b)
            if (result.operands.size == 1) return result.operands[0]
            return result
        }

        private fun filterPrecedencePredicates(collection: MutableCollection<out SemanticContext>): List<PrecedencePredicate> {
            val result = mutableListOf<PrecedencePredicate>()
            val iter = collection.iterator()
            while (iter.hasNext()) {
                val context = iter.next()
                if (context is PrecedencePredicate) {
                    result.add(context)
                    iter.remove()
                }
            }
            return result
        }
    }
}
