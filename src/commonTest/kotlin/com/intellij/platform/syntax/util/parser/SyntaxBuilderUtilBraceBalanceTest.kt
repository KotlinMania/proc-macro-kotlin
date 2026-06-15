// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.parser

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.buildTokenList
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.areBracesBalancedInside
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.isBalancedBlock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SyntaxBuilderUtilBraceBalanceTest {
    companion object {
        private val L_CURLY = SyntaxElementType("{")
        private val R_CURLY = SyntaxElementType("}")
        private val L_BRACKET = SyntaxElementType("[")
        private val R_BRACKET = SyntaxElementType("]")
        private val OTHER = SyntaxElementType("OTHER")
    }

    @Test
    fun balancedBlockSimpleObject() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("x", OTHER)
                token("}", R_CURLY)
            }
        assertTrue(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun balancedBlockEmptyBraces() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("}", R_CURLY)
            }
        assertTrue(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun balancedBlockNested() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("{", L_CURLY)
                token("}", R_CURLY)
                token("}", R_CURLY)
            }
        assertTrue(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun balancedBlockEmptyTokenList() {
        val tokens = buildTokenList {}
        assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun balancedBlockNoLeftBrace() {
        val tokens =
            buildTokenList {
                token("x", OTHER)
                token("}", R_CURLY)
            }
        assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun balancedBlockMissingRightBrace() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("x", OTHER)
            }
        assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun balancedBlockExtraRight() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("}", R_CURLY)
                token("}", R_CURLY)
            }
        assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun balancedBlockClosingBraceNotLast() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("}", R_CURLY)
                token("x", OTHER)
            }
        assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun balancedBlockWrongBraceKind() {
        val tokens =
            buildTokenList {
                token("[", L_BRACKET)
                token("]", R_BRACKET)
            }
        assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun balancedBlockUnbalancedNested() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("{", L_CURLY)
                token("}", R_CURLY)
            }
        assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun balancedBlockIgnoresOtherBraceKinds() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("[", L_BRACKET)
                token("}", R_CURLY)
            }
        assertTrue(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun balancedBlockSingleLeftBrace() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
            }
        assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun balancedBlockTwoConsecutivePairs() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("}", R_CURLY)
                token("{", L_CURLY)
                token("}", R_CURLY)
            }
        assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun bracesInsideEmptyTokenList() {
        val tokens = buildTokenList {}
        assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun bracesInsideNoBraces() {
        val tokens =
            buildTokenList {
                token("x", OTHER)
                token("y", OTHER)
            }
        assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun bracesInsideBalancedPair() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("x", OTHER)
                token("}", R_CURLY)
            }
        assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun bracesInsideTwoConsecutivePairs() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("}", R_CURLY)
                token("{", L_CURLY)
                token("}", R_CURLY)
            }
        assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun bracesInsideNestedBalanced() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("{", L_CURLY)
                token("}", R_CURLY)
                token("}", R_CURLY)
            }
        assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun bracesInsideRightBeforeLeft() {
        val tokens =
            buildTokenList {
                token("}", R_CURLY)
                token("{", L_CURLY)
            }
        assertFalse(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun bracesInsideExtraRight() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("}", R_CURLY)
                token("}", R_CURLY)
            }
        assertFalse(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun bracesInsideExtraLeft() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("{", L_CURLY)
                token("}", R_CURLY)
            }
        assertFalse(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun bracesInsideSingleRight() {
        val tokens =
            buildTokenList {
                token("}", R_CURLY)
            }
        assertFalse(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun bracesInsideSingleLeft() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
            }
        assertFalse(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun bracesInsideIgnoresOtherBraceKinds() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("[", L_BRACKET)
                token("}", R_CURLY)
            }
        assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun bracesInsideDoesNotRequireFirstToken() {
        val tokens =
            buildTokenList {
                token("x", OTHER)
                token("{", L_CURLY)
                token("}", R_CURLY)
            }
        assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun bracesInsideDoesNotRequireLastToken() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("}", R_CURLY)
                token("x", OTHER)
            }
        assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun bracesInsideGoesNegativeMidStream() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("}", R_CURLY)
                token("}", R_CURLY)
                token("{", L_CURLY)
            }
        assertFalse(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun bracketBalanceInsideObject() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("[", L_BRACKET)
                token("x", OTHER)
                token("]", R_BRACKET)
                token("}", R_CURLY)
            }
        assertTrue(areBracesBalancedInside(tokens, L_BRACKET, R_BRACKET, null))
    }

    @Test
    fun missingRightBracketInsideObject() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("[", L_BRACKET)
                token("x", OTHER)
                token("}", R_CURLY)
            }
        assertFalse(areBracesBalancedInside(tokens, L_BRACKET, R_BRACKET, null))
    }

    @Test
    fun extraRightBracketInsideObject() {
        val tokens =
            buildTokenList {
                token("{", L_CURLY)
                token("]", R_BRACKET)
                token("x", OTHER)
                token("}", R_CURLY)
            }
        assertFalse(areBracesBalancedInside(tokens, L_BRACKET, R_BRACKET, null))
    }

    @Test
    fun braceBalanceInsideArray() {
        val tokens =
            buildTokenList {
                token("[", L_BRACKET)
                token("{", L_CURLY)
                token("x", OTHER)
                token("}", R_CURLY)
                token("]", R_BRACKET)
            }
        assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
    }

    @Test
    fun extraRightBraceInsideArray() {
        val tokens =
            buildTokenList {
                token("[", L_BRACKET)
                token("}", R_CURLY)
                token("x", OTHER)
                token("]", R_BRACKET)
            }
        assertFalse(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
    }
}
