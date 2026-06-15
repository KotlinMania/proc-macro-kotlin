package io.github.kotlinmania.procmacro.bridge

import io.github.kotlinmania.procmacro.Delimiter
import io.github.kotlinmania.procmacro.Group
import io.github.kotlinmania.procmacro.Ident
import io.github.kotlinmania.procmacro.Literal
import io.github.kotlinmania.procmacro.Punct
import io.github.kotlinmania.procmacro.Spacing
import io.github.kotlinmania.procmacro.Span
import io.github.kotlinmania.procmacro.TokenStream
import io.github.kotlinmania.procmacro.TokenTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BridgeShapeTest {
    @Test
    fun tokenTreeRoundTripsThroughBridgeShape() {
        val stream =
            TokenStream.fromTokenTrees(
                listOf(
                    TokenTree.Ident(Ident.new("alpha", Span.callSite())),
                    TokenTree.Punct(Punct.new('+', Spacing.ALONE)),
                    TokenTree.Literal(Literal.i32Unsuffixed(7)),
                ),
            )
        val group = TokenTree.Group(Group.new(Delimiter.PARENTHESIS, stream))

        val bridgeTree = group.toBridgeTree()
        val bridgeGroup = assertIs<BridgeTokenTree.Group<ClientTokenStream, ClientSpan>>(bridgeTree).value

        assertEquals(Delimiter.PARENTHESIS, bridgeGroup.delimiter)
        assertEquals("(alpha + 7)", BridgeMethods.tsFromTokenTree(bridgeTree).stream.toString())
    }

    @Test
    fun clientMethodsConcatenateTreesAndStreams() {
        val alpha = TokenTree.Ident(Ident.new("alpha", Span.callSite())).toBridgeTree()
        val plus = TokenTree.Punct(Punct.new('+', Spacing.ALONE)).toBridgeTree()
        val seven = TokenTree.Literal(Literal.i32Unsuffixed(7)).toBridgeTree()

        val left = BridgeMethods.tsConcatTrees(null, listOf(alpha, plus))
        val out = BridgeMethods.tsConcatStreams(left, listOf(BridgeMethods.tsFromTokenTree(seven)))

        assertEquals("alpha + 7", BridgeMethods.tsToString(out))
        assertFalse(BridgeMethods.tsIsEmpty(out))
        assertEquals(3, BridgeMethods.tsIntoTrees(out).size)
    }

    @Test
    fun symbolValidationKeepsRawIdentRules() {
        assertIs<Result.Ok<Symbol>>(Symbol.normalizeAndValidateIdent("valid_name"))
        assertIs<Result.Err>(Symbol.normalizeAndValidateIdent("1invalid"))
        assertIs<Result.Ok<Symbol>>(Symbol.normalizeAndValidateRawIdent("type"))
        assertIs<Result.Err>(Symbol.normalizeAndValidateRawIdent("self"))
    }

    @Test
    fun bridgeStateReportsAvailabilityOnlyInsideEnter() {
        assertFalse(BridgeClientState.isAvailable())

        val state =
            BridgeState(
                globals =
                    ExpnGlobals(
                        defSite = ClientSpan(Span.defSite()),
                        callSite = ClientSpan(Span.callSite()),
                        mixedSite = ClientSpan(Span.mixedSite()),
                    ),
                dispatch = BridgeDispatch { it.copy() },
            )

        BridgeClientState.enter(state) {
            assertTrue(BridgeClientState.isAvailable())
            RpcBuffer()
        }

        assertFalse(BridgeClientState.isAvailable())
    }
}
