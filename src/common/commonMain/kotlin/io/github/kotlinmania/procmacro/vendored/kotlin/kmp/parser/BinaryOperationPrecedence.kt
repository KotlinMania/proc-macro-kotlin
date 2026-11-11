/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// Vendored from JetBrains/kotlin compiler/multiplatform-parsing/common/src/org/jetbrains/kotlin/kmp/parser/BinaryOperationPrecedence.kt
package io.github.kotlinmania.procmacro.vendored.kotlin.kmp.parser

import io.github.kotlinmania.procmacro.vendored.intellij.platform.syntax.SyntaxElementType
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.lexer.KtTokens
import io.github.kotlinmania.procmacro.vendored.kotlin.kmp.lexer.KtTokens.SOFT_KEYWORDS_AND_MODIFIERS

enum class BinaryOperationPrecedence(
    val higherPriority: BinaryOperationPrecedence?,
    vararg rawTokens: SyntaxElementType,
) {
    AS(null, KtTokens.AS_KEYWORD, KtTokens.AS_SAFE),
    MULTIPLICATIVE(AS, KtTokens.MUL, KtTokens.DIV, KtTokens.PERC),
    ADDITIVE(MULTIPLICATIVE, KtTokens.PLUS, KtTokens.MINUS),
    RANGE(ADDITIVE, KtTokens.RANGE, KtTokens.RANGE_UNTIL),
    INFIX(RANGE, KtTokens.IDENTIFIER),
    ELVIS(INFIX, KtTokens.ELVIS),
    IN_OR_IS(ELVIS, KtTokens.IN_MODIFIER, KtTokens.NOT_IN, KtTokens.IS_KEYWORD, KtTokens.NOT_IS),
    COMPARISON(IN_OR_IS, KtTokens.LT, KtTokens.GT, KtTokens.LTEQ, KtTokens.GTEQ),
    EQUALITY(COMPARISON, KtTokens.EQEQ, KtTokens.EXCLEQ, KtTokens.EQEQEQ, KtTokens.EXCLEQEQEQ),
    CONJUNCTION(EQUALITY, KtTokens.ANDAND),
    DISJUNCTION(CONJUNCTION, KtTokens.OROR),
    ASSIGNMENT(DISJUNCTION, KtTokens.EQ, KtTokens.PLUSEQ, KtTokens.MINUSEQ, KtTokens.MULTEQ, KtTokens.DIVEQ, KtTokens.PERCEQ),
    ;

    val tokens: List<SyntaxElementType> = rawTokens.toList()

    companion object {
        /**
         * Defines a map, where each token is mapped on its binary precedence.
         *
         * It's used for fast lookup over binary precedences by a provided token.
         * It works with O(1) complexity in a given use-site instead of O(N) where N is a number of binary precedences (currently 12).
         */
        val TOKEN_TO_BINARY_PRECEDENCE_MAP_WITH_SOFT_IDENTIFIERS: Map<SyntaxElementType, BinaryOperationPrecedence> =
            getTokensToBinaryPrecedenceMap(includeSoftIdentifiers = true)

        val TOKEN_TO_BINARY_PRECEDENCE_MAP: Map<SyntaxElementType, BinaryOperationPrecedence> =
            getTokensToBinaryPrecedenceMap(includeSoftIdentifiers = false)

        private fun getTokensToBinaryPrecedenceMap(includeSoftIdentifiers: Boolean): Map<SyntaxElementType, BinaryOperationPrecedence> {
            val result = HashMap<SyntaxElementType, BinaryOperationPrecedence>()

            fun register(
                elementType: SyntaxElementType,
                precedence: BinaryOperationPrecedence,
            ) {
                val existingPrecedence = result.put(elementType, precedence)
                require(existingPrecedence == null) {
                    "All binary precedences have unique operations. The $elementType is already assigned to $existingPrecedence."
                }
            }

            for (entry in entries) {
                for (type in entry.tokens) {
                    register(type, entry)

                    if (type === KtTokens.IDENTIFIER && includeSoftIdentifiers) {
                        for (softKeyword in SOFT_KEYWORDS_AND_MODIFIERS) {
                            register(softKeyword, entry)
                        }
                    }
                }
            }

            return result
        }
    }
}
