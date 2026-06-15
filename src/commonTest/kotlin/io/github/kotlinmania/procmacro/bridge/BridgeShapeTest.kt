package io.github.kotlinmania.procmacro.bridge

import io.github.kotlinmania.procmacro.Delimiter
import io.github.kotlinmania.procmacro.Group
import io.github.kotlinmania.procmacro.Ident
import io.github.kotlinmania.procmacro.Level
import io.github.kotlinmania.procmacro.Literal
import io.github.kotlinmania.procmacro.Punct
import io.github.kotlinmania.procmacro.Spacing
import io.github.kotlinmania.procmacro.Span
import io.github.kotlinmania.procmacro.SpanData
import io.github.kotlinmania.procmacro.TokenStream
import io.github.kotlinmania.procmacro.TokenTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
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
        assertIs<Result.Err>(Symbol.normalizeAndValidateRawIdent("_"))
        assertFailsWith<IllegalArgumentException> {
            Ident.newRaw("_", Span.callSite())
        }
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

    @Test
    fun bridgeStateRestoresOuterStateAfterNestedEnter() {
        val outer =
            BridgeState(
                globals = defaultClientGlobals(),
                dispatch = BridgeDispatch { RpcBuffer(payload = BridgePayload.Response.StringValue("outer")) },
            )
        val inner =
            BridgeState(
                globals = defaultClientGlobals(),
                dispatch = BridgeDispatch { RpcBuffer(payload = BridgePayload.Response.StringValue("inner")) },
            )

        BridgeClientState.enter(outer) {
            assertEquals("outer", BridgeMethods.injectedEnvVar("ignored"))
            BridgeClientState.enter(inner) {
                assertEquals("inner", BridgeMethods.injectedEnvVar("ignored"))
                RpcBuffer()
            }
            assertEquals("outer", BridgeMethods.injectedEnvVar("ignored"))
            RpcBuffer()
        }
    }

    @Test
    fun clientRequestsRouteThroughBridgeDispatch() {
        val seen = mutableListOf<BridgePayload.Request>()
        val state =
            BridgeState(
                globals = defaultClientGlobals(),
                dispatch =
                    BridgeDispatch { buffer ->
                        val request = assertIs<BridgePayload.Request>(buffer.payload)
                        seen.add(request)
                        when (request) {
                            is BridgePayload.Request.InjectedEnvVar ->
                                RpcBuffer(payload = BridgePayload.Response.StringValue("VALUE"))
                            is BridgePayload.Request.TrackEnvVar,
                            is BridgePayload.Request.TrackPath,
                            -> RpcBuffer(payload = BridgePayload.Response.UnitValue)
                            is BridgePayload.Request.SpanSourceText ->
                                RpcBuffer(payload = BridgePayload.Response.StringValue("source text"))
                        }
                    },
            )

        BridgeClientState.enter(state) {
            assertEquals("VALUE", BridgeMethods.injectedEnvVar("NAME"))
            BridgeMethods.trackEnvVar("TRACKED", "present")
            BridgeMethods.trackPath("src/lib.rs")
            assertEquals("source text", BridgeMethods.spanSourceText(ClientSpan(Span.callSite())))
            RpcBuffer()
        }

        assertIs<BridgePayload.Request.InjectedEnvVar>(seen[0])
        assertIs<BridgePayload.Request.TrackEnvVar>(seen[1])
        assertIs<BridgePayload.Request.TrackPath>(seen[2])
        assertIs<BridgePayload.Request.SpanSourceText>(seen[3])
    }

    @Test
    fun serverGlobalsArePassedIntoClientExecution() {
        val callSite = Span(SpanData.Synthetic(10..11))
        val globals =
            ExpnGlobals(
                defSite = ClientSpan(Span(SpanData.Synthetic(1..2))),
                callSite = ClientSpan(callSite),
                mixedSite = ClientSpan(Span(SpanData.Synthetic(20..21))),
            )
        val server =
            object : BridgeServer {
                override fun globals(): ExpnGlobals<ClientSpan> = globals
            }

        SameThread.runBridgeAndClient(
            dispatcher = Dispatcher(server),
            input = RpcBuffer(),
            runClient = { config ->
                assertTrue(
                    config.globals.callSite.span
                        .eq(callSite),
                )
                assertTrue(
                    BridgeClientState
                        .withState()
                        .globals.callSite.span
                        .eq(callSite),
                )
                RpcBuffer()
            },
            forceShowPanics = false,
        )
    }

    @Test
    fun crossThreadExecutionFailsFastWithoutRunner() {
        assertFailsWith<UnsupportedOperationException> {
            CrossThread.runBridgeAndClient(
                dispatcher = Dispatcher(object : BridgeServer {}),
                input = RpcBuffer(),
                runClient = { RpcBuffer() },
                forceShowPanics = false,
            )
        }
    }

    @Test
    fun bridgeLiteralParserAcceptsRustShapedLiteralPrefixes() {
        val byte = BridgeMethods.literalFromStr("b'a'").literal()
        assertEquals(BridgeLitKind.Byte, byte.kind)
        assertEquals("a", byte.symbol.asString())

        val byteString = BridgeMethods.literalFromStr("b\"hi\"").literal()
        assertEquals(BridgeLitKind.ByteStr, byteString.kind)
        assertEquals("hi", byteString.symbol.asString())

        val cString = BridgeMethods.literalFromStr("c\"hi\"").literal()
        assertEquals(BridgeLitKind.CStr, cString.kind)
        assertEquals("hi", cString.symbol.asString())

        val raw = BridgeMethods.literalFromStr("br##\"hi\"##suffix").literal()
        assertEquals(BridgeLitKind.ByteStrRaw(2), raw.kind)
        assertEquals("hi", raw.symbol.asString())
        assertEquals("suffix", raw.suffix?.asString())
    }

    @Test
    fun tokenStreamCloneCopiesNestedTokenGraph() {
        val originalIdent = TokenTree.Ident(Ident.new("x", Span.callSite()))
        val originalGroup =
            TokenTree.Group(
                Group.new(
                    Delimiter.PARENTHESIS,
                    TokenStream.fromTokenTree(originalIdent),
                ),
            )
        val original = ClientTokenStream(TokenStream.fromTokenTree(originalGroup))
        val clone = BridgeMethods.tsClone(original)

        val clonedGroup =
            assertIs<TokenTree.Group>(
                clone.stream.data.trees
                    .single(),
            ).value
        val clonedIdent =
            assertIs<TokenTree.Ident>(
                clonedGroup
                    .stream()
                    .data.trees
                    .single(),
            ).value
        clonedIdent.setSpan(Span.mixedSite())

        assertTrue(originalIdent.value.span().eq(Span.callSite()))
        assertTrue(clonedIdent.span().eq(Span.mixedSite()))
    }

    @Test
    fun spanSubspanUsesRelativeOffsets() {
        val span = ClientSpan(Span(SpanData.Synthetic(10..19)))

        val subspan = BridgeMethods.spanSubspan(span, start = 0, end = 5)

        assertEquals(10..14, subspan?.span?.byteRange())
        assertNull(BridgeMethods.spanSubspan(span, start = -1, end = 1))
        assertNull(BridgeMethods.spanSubspan(span, start = 0, end = 11))
        assertNull(BridgeMethods.spanSubspan(span, start = 6, end = 5))
    }

    @Test
    fun bridgeDiagnosticsPreserveChildren() {
        val child =
            BridgeDiagnostic<ClientSpan>(
                level = Level.HELP,
                message = "child",
                spans = emptyList<ClientSpan>(),
                children = emptyList<BridgeDiagnostic<ClientSpan>>(),
            )
        val root =
            BridgeDiagnostic<ClientSpan>(
                level = Level.ERROR,
                message = "root",
                spans = emptyList<ClientSpan>(),
                children = listOf(child),
            ).toPublicDiagnostic()

        val children = root.children()
        assertTrue(children.hasNext())
        val publicChild = children.next()
        assertEquals(Level.HELP, publicChild.level())
        assertEquals("child", publicChild.message())
        assertFalse(children.hasNext())
    }
}

private fun Result<BridgeLiteral<ClientSpan>>.literal(): BridgeLiteral<ClientSpan> =
    assertIs<Result.Ok<BridgeLiteral<ClientSpan>>>(this).value
