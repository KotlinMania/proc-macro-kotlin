package io.github.kotlinmania.procmacro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KotlinTokenAdapterFidelityTest {
    @Test
    fun nestedQuotesInsideInterpolationRemainOneLiteral() {
        val literal = "\"\${items.joinToString(\"\\n\")}\""
        val tokens = TokenStream.fromString("val rendered = $literal").getOrThrow().toList()
        assertEquals(4, tokens.size)
        assertEquals(literal, assertIs<TokenTree.Literal>(tokens.last()).value.toString())
    }

    @Test
    fun mismatchedDelimiterKindsAreErrors() {
        for (source in listOf("(]", "{)", "[}", "([)]")) {
            assertTrue(TokenStream.fromString(source).isFailure, source)
        }
    }

    @Test
    fun kotlinEscapedIdentifiersKeepTheirExactSpelling() {
        val tokens = TokenStream.fromString("val `when` = 1").getOrThrow().toList()
        assertEquals("`when`", assertIs<TokenTree.Ident>(tokens[1]).value.toString())
    }
}
