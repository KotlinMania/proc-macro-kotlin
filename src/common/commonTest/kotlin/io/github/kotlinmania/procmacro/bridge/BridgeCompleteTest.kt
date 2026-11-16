package io.github.kotlinmania.procmacro.bridge

import io.github.kotlinmania.procmacro.Diagnostic
import io.github.kotlinmania.procmacro.Ident
import io.github.kotlinmania.procmacro.Level
import io.github.kotlinmania.procmacro.Literal
import io.github.kotlinmania.procmacro.Punct
import io.github.kotlinmania.procmacro.Spacing
import io.github.kotlinmania.procmacro.Span
import io.github.kotlinmania.procmacro.SpanData
import io.github.kotlinmania.procmacro.TokenStream
import io.github.kotlinmania.procmacro.TokenTree
import io.github.kotlinmania.procmacro.TrackedEnvVarOutcome
import io.github.kotlinmania.procmacro.tracked.envVar
import io.github.kotlinmania.procmacro.tracked.path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BridgeCompleteTest {
    @Test
    fun fxHasherAndFxHashMapStoreAndClearEntries() {
        val hasherA = FxHasher.default()
        hasherA.write("alpha".encodeToByteArray())
        val hasherB = FxHasher.default()
        hasherB.write("alpha".encodeToByteArray())
        assertEquals(hasherA.finish(), hasherB.finish())

        val map = FxHashMap.default<String, Int>()
        assertTrue(map.isEmpty())
        assertEquals(7, map.getOrPut("seven") { 7 })
        assertEquals(7, map.getOrPut("seven") { 99 })
        assertEquals(7, map["seven"])
        assertEquals(1, map.size)
        map.clear()
        assertTrue(map.isEmpty())
    }

    @Test
    fun arenaAllocatesSmallAndHugeByteSlicesWithoutAliasing() {
        val arena = Arena.new()
        val hello = arena.allocStr("hello arena")
        assertEquals("hello arena", hello)

        val hugeInput = ByteArray(HUGE_PAGE + 17) { (it and 0x7f).toByte() }
        val slice = arena.allocRaw(hugeInput.size)
        slice.writeCopyOfSlice(hugeInput)
        assertContentEquals(hugeInput, slice.toByteArray())
    }

    @Test
    fun bufferGrowsTakesReadsAndDropsCleanly() {
        val buf = Buffer.new()
        assertTrue(buf.isEmpty())
        val payload = ByteArray(200) { (it % 251).toByte() }
        buf.extendFromSlice(payload)
        assertEquals(200, buf.size)

        val taken = buf.take()
        assertTrue(buf.isEmpty())
        assertEquals(200, taken.size)

        val reader = taken.toReader()
        assertEquals(payload[0].toUByte(), reader.readUByte())
        assertEquals(199, reader.remaining)
        assertContentEquals(payload.copyOfRange(1, 200), reader.readBytes(199))
        assertTrue(reader.isEmpty())

        taken.drop()
        assertTrue(taken.isEmpty())
    }

    @Test
    fun symbolInternerInvalidationDetectsUseAfterFree() {
        Symbol.invalidateAll()
        val sym = Symbol.new("transientIdent")
        assertEquals("transientIdent", sym.with { it })

        Symbol.invalidateAll()
        assertFailsWith<IllegalStateException> {
            sym.asString()
        }

        val fresh = Symbol.new("freshIdent")
        assertEquals("freshIdent", fresh.asString())
    }

    @Test
    fun handleStoreDeduplicatesSpansAndTransfersOwnedStreams() {
        val store = HandleStore.new<BridgeServer>(HandleCounters())
        val spanA = Marked.mark<ClientSpan, ClientSpan>(ClientSpan(Span(SpanData.Synthetic(5..9))))
        val spanB = Marked.mark<ClientSpan, ClientSpan>(ClientSpan(Span(SpanData.Synthetic(5..9))))

        val h1 = store.span.alloc(spanA)
        val h2 = store.span.alloc(spanB)
        assertEquals(h1, h2)
        val copiedSpan = store.span.copy(h1).unmark()
        assertEquals(5..9, copiedSpan.span.byteRange())

        val ts =
            Marked.mark<ClientTokenStream, ClientTokenStream>(
                ClientTokenStream(TokenStream.fromTokenTree(TokenTree.Ident(Ident.new("tok", Span.callSite())))),
            )
        val tsHandle = store.tokenStream.alloc(ts)
        val storedStream = store.tokenStream[tsHandle].unmark()
        assertEquals("tok", storedStream.stream.toString())
        val takenStream = store.tokenStream.take(tsHandle).unmark()
        assertEquals("tok", takenStream.stream.toString())
        assertFailsWith<IllegalArgumentException> {
            store.tokenStream.take(tsHandle)
        }
    }

    @Test
    fun messagePipeAndSameThreadGuardEnforceInvariants() {
        val (left, right) = MessagePipe.new<String>()
        left.send("ping")
        assertEquals("ping", right.recv())
        right.send("pong")
        assertEquals("pong", left.recv())
        left.close()
        assertNull(right.recv())

        val guard = RunningSameThreadGuard.new()
        try {
            assertFailsWith<IllegalStateException> {
                RunningSameThreadGuard.new()
            }
        } finally {
            guard.drop()
        }
    }

    @Test
    fun bangAttrAndCustomDeriveMacrosExecuteEndToEndOverBinaryRpc() {
        val trackedPaths = mutableListOf<String>()
        val trackedVars = mutableMapOf<String, String?>()
        val diagnostics = mutableListOf<BridgeDiagnostic<ClientSpan>>()
        val customCallSite = Span(SpanData.Synthetic(40..49))

        val server =
            object : BridgeServer {
                override fun globals(): ExpnGlobals<ClientSpan> =
                    ExpnGlobals(
                        defSite = ClientSpan(Span(SpanData.Synthetic(1..4))),
                        callSite = ClientSpan(customCallSite),
                        mixedSite = ClientSpan(Span(SpanData.Synthetic(10..14))),
                    )

                override fun injectedEnvVar(variable: String): String? =
                    if (variable == "PROC_MACRO_FEATURE") "enabled" else null

                override fun trackEnvVar(
                    variable: String,
                    value: String?,
                ) {
                    trackedVars[variable] = value
                }

                override fun trackPath(path: String) {
                    trackedPaths.add(path)
                }

                override fun emitDiagnostic(diagnostic: BridgeDiagnostic<ClientSpan>) {
                    diagnostics.add(diagnostic)
                }

                override fun spanSourceText(span: ClientSpan): String? =
                    if (span.span.eq(customCallSite)) "my_macro!(alpha)" else null

                override fun tsExpandExpr(stream: ClientTokenStream): Result<ClientTokenStream> =
                    Result.Ok(
                        ClientTokenStream(
                            TokenStream.fromTokenTree(TokenTree.Literal(Literal.i32Unsuffixed(42))),
                        ),
                    )
            }

        val bang =
            assertIs<ProcMacro.Bang>(
                ProcMacro.bang("compute") { input ->
                    assertTrue(Span.callSite().eq(customCallSite))
                    assertEquals("my_macro!(alpha)", Span.callSite().sourceText())

                    val envOutcome = envVar("PROC_MACRO_FEATURE")
                    assertIs<TrackedEnvVarOutcome.Ok>(envOutcome)
                    assertEquals("enabled", envOutcome.value)
                    path("schema/input.graphql")

                    Diagnostic.spanned(Span.callSite(), Level.NOTE, "expanding compute").emit()

                    val parsedLit = Literal.fromStrUnchecked("\"ok\"")
                    val expanded = input.expandExpr().getOrThrow()
                    TokenStream.fromTokenTrees(
                        input.toList() +
                            listOf(
                                TokenTree.Punct(Punct.new('=', Spacing.ALONE)),
                                TokenTree.Literal(parsedLit),
                            ) +
                            expanded.toList(),
                    )
                },
            )
        assertEquals("compute", bang.name())

        val inputStream =
            ClientTokenStream(
                TokenStream.fromTokenTree(TokenTree.Ident(Ident.new("alpha", customCallSite))),
            )
        val bangOutcome = bang.client.run(SameThread, server, inputStream, forceShowPanics = false)
        val bangOut = assertIs<PanicOutcome.Ok<ClientTokenStream>>(bangOutcome).value
        assertEquals("alpha = \"ok\" 42", bangOut.stream.toString())
        assertEquals(listOf("schema/input.graphql"), trackedPaths)
        assertEquals("enabled", trackedVars["PROC_MACRO_FEATURE"])
        assertEquals(1, diagnostics.size)
        assertEquals("expanding compute", diagnostics.single().message)

        val attr =
            assertIs<ProcMacro.Attr>(
                ProcMacro.attr("route") { args, item ->
                    TokenStream.fromTokenTrees(args.toList() + item.toList())
                },
            )
        assertEquals("route", attr.name())
        val attrOut =
            attr.client
                .run(
                    SameThread,
                    server,
                    ClientTokenStream(TokenStream.fromTokenTree(TokenTree.Ident(Ident.new("get", Span.callSite())))),
                    ClientTokenStream(TokenStream.fromTokenTree(TokenTree.Ident(Ident.new("handler", Span.callSite())))),
                    forceShowPanics = false,
                ).unwrapOrThrow()
        assertEquals("get handler", attrOut.stream.toString())

        val derive =
            assertIs<ProcMacro.CustomDerive>(
                ProcMacro.customDerive("MyTrait", listOf("helper")) { _ ->
                    error("derive boom")
                },
            )
        assertEquals("MyTrait", derive.name())
        val panicked =
            derive.client.run(
                SameThread,
                server,
                ClientTokenStream(TokenStream.new()),
                forceShowPanics = false,
            )
        val panicMessage = assertIs<PanicOutcome.Panicked>(panicked).panic
        assertEquals("derive boom", panicMessage.asStr())
    }

    @Test
    fun publicBridgeClientStateRunnersEnterAndExitActiveSession() {
        assertFalse(BridgeClientState.isAvailable())

        val bangInput = TokenStream.fromTokenTree(TokenTree.Ident(Ident.new("value", Span.callSite())))
        val bangOutput =
            BridgeClientState.runBangMacro("mirror", bangInput) { stream ->
                assertTrue(BridgeClientState.isAvailable())
                TokenStream.fromTokenTrees(
                    stream.toList() + listOf(TokenTree.Literal(Literal.i32Unsuffixed(7))),
                )
            }
        assertFalse(BridgeClientState.isAvailable())
        assertEquals("value 7", bangOutput.toString())

        val attrStream = TokenStream.fromTokenTree(TokenTree.Ident(Ident.new("inline", Span.callSite())))
        val itemStream = TokenStream.fromTokenTree(TokenTree.Ident(Ident.new("target", Span.callSite())))
        val attrOutput =
            BridgeClientState.runAttrMacro("tag", attrStream, itemStream) { attr, item ->
                assertTrue(BridgeClientState.isAvailable())
                TokenStream.fromTokenTrees(attr.toList() + item.toList())
            }
        assertFalse(BridgeClientState.isAvailable())
        assertEquals("inline target", attrOutput.toString())

        val deriveOutput =
            BridgeClientState.runCustomDeriveMacro("Cloneable", listOf("cloneAttr"), itemStream) { item ->
                assertTrue(BridgeClientState.isAvailable())
                item
            }
        assertFalse(BridgeClientState.isAvailable())
        assertEquals("target", deriveOutput.toString())
    }
}
