// port-lint: source src/bridge/server.rs
package io.github.kotlinmania.procmacro.bridge

import io.github.kotlinmania.procmacro.TokenStream
import io.github.kotlinmania.threadlocal.ThreadLocal

/**
 * Server-side handle tables tracking owned token streams and interned spans
 * across an active procedural macro RPC session.
 */
internal class HandleStore<S : BridgeServer>(
    val tokenStream: OwnedStore<Marked<ClientTokenStream, ClientTokenStream>>,
    val span: InternedStore<Marked<ClientSpan, ClientSpan>>,
) {
    fun encodeTokenStream(
        w: Buffer,
        value: Marked<ClientTokenStream, ClientTokenStream>,
    ) {
        tokenStream.alloc(value).encode(w)
    }

    fun decodeTokenStream(r: BufferReader): Marked<ClientTokenStream, ClientTokenStream> =
        tokenStream.take(Handle.decode(r))

    fun borrowTokenStream(r: BufferReader): Marked<ClientTokenStream, ClientTokenStream> =
        tokenStream[Handle.decode(r)]

    fun encodeSpan(
        w: Buffer,
        value: Marked<ClientSpan, ClientSpan>,
    ) {
        span.alloc(value).encode(w)
    }

    fun decodeSpan(r: BufferReader): Marked<ClientSpan, ClientSpan> =
        span.copy(Handle.decode(r))

    fun encodeTree(
        w: Buffer,
        tree: BridgeTokenTree<ClientTokenStream, ClientSpan>,
    ) {
        RpcCodec.encodeTokenTree(
            w,
            tree,
            encodeStream = { buf, ts -> encodeTokenStream(buf, Marked.mark(ts)) },
            encodeSpan = { buf, sp -> encodeSpan(buf, Marked.mark(sp)) },
        )
    }

    fun decodeTree(r: BufferReader): BridgeTokenTree<ClientTokenStream, ClientSpan> =
        RpcCodec.decodeTokenTree(
            r,
            decodeStream = { reader -> decodeTokenStream(reader).unmark() },
            decodeSpan = { reader -> decodeSpan(reader).unmark() },
        )

    companion object {
        fun <S : BridgeServer> new(handleCounters: HandleCounters = HandleCounters()): HandleStore<S> =
            HandleStore(
                tokenStream = OwnedStore.new(handleCounters.tokenStream),
                span = InternedStore.new(handleCounters.span),
            )
    }
}

internal interface BridgeServer {
    fun globals(): ExpnGlobals<ClientSpan> = defaultClientGlobals()

    fun internSymbol(ident: String): Symbol = Symbol.intern(ident)

    fun withSymbolString(
        symbol: Symbol,
        block: (String) -> Unit,
    ) {
        block(symbol.asString())
    }

    fun injectedEnvVar(variable: String): String? = null

    fun trackEnvVar(
        variable: String,
        value: String?,
    ) {
    }

    fun trackPath(path: String) {
    }

    fun literalFromStr(s: String): Result<BridgeLiteral<ClientSpan>> = BridgeMethods.literalFromStr(s)

    fun emitDiagnostic(diagnostic: BridgeDiagnostic<ClientSpan>) {
        diagnostic.toPublicDiagnostic().emitLocally()
    }

    fun tsDrop(stream: ClientTokenStream) {
        BridgeMethods.tsDrop(stream)
    }

    fun tsClone(stream: ClientTokenStream): ClientTokenStream = BridgeMethods.tsClone(stream)

    fun tsIsEmpty(stream: ClientTokenStream): Boolean = BridgeMethods.tsIsEmpty(stream)

    fun tsExpandExpr(stream: ClientTokenStream): Result<ClientTokenStream> = stream.expandExprLocally()

    fun tsFromStr(src: String): Result<ClientTokenStream> = BridgeMethods.tsFromStr(src)

    fun tsToString(stream: ClientTokenStream): String = BridgeMethods.tsToString(stream)

    fun tsFromTokenTree(tree: BridgeTokenTree<ClientTokenStream, ClientSpan>): ClientTokenStream =
        BridgeMethods.tsFromTokenTree(tree)

    fun tsConcatTrees(
        base: ClientTokenStream?,
        trees: List<BridgeTokenTree<ClientTokenStream, ClientSpan>>,
    ): ClientTokenStream = BridgeMethods.tsConcatTrees(base, trees)

    fun tsConcatStreams(
        base: ClientTokenStream?,
        streams: List<ClientTokenStream>,
    ): ClientTokenStream = BridgeMethods.tsConcatStreams(base, streams)

    fun tsIntoTrees(stream: ClientTokenStream): List<BridgeTokenTree<ClientTokenStream, ClientSpan>> =
        BridgeMethods.tsIntoTrees(stream)

    fun spanDebug(span: ClientSpan): String = BridgeMethods.spanDebug(span)

    fun spanParent(span: ClientSpan): ClientSpan? = BridgeMethods.spanParent(span)

    fun spanSource(span: ClientSpan): ClientSpan = BridgeMethods.spanSource(span)

    fun spanByteRange(span: ClientSpan): IntRange = BridgeMethods.spanByteRange(span)

    fun spanStart(span: ClientSpan): ClientSpan = BridgeMethods.spanStart(span)

    fun spanEnd(span: ClientSpan): ClientSpan = BridgeMethods.spanEnd(span)

    fun spanLine(span: ClientSpan): Int = BridgeMethods.spanLine(span)

    fun spanColumn(span: ClientSpan): Int = BridgeMethods.spanColumn(span)

    fun spanFile(span: ClientSpan): String = BridgeMethods.spanFile(span)

    fun spanLocalFile(span: ClientSpan): String? = BridgeMethods.spanLocalFile(span)

    fun spanJoin(
        span: ClientSpan,
        other: ClientSpan,
    ): ClientSpan? = BridgeMethods.spanJoin(span, other)

    fun spanSubspan(
        span: ClientSpan,
        start: Int,
        end: Int,
    ): ClientSpan? = BridgeMethods.spanSubspan(span, start, end)

    fun spanResolvedAt(
        span: ClientSpan,
        at: ClientSpan,
    ): ClientSpan = BridgeMethods.spanResolvedAt(span, at)

    fun spanSourceText(span: ClientSpan): String? = span.span.localSourceText()

    fun spanSaveSpan(span: ClientSpan): Int = BridgeMethods.spanSaveSpan(span)

    fun spanRecoverProcMacroSpan(id: Int): ClientSpan = BridgeMethods.spanRecoverProcMacroSpan(id)

    fun symbolNormalizeAndValidateIdent(string: String): Result<Symbol> =
        Symbol.normalizeAndValidateIdent(string)

    fun dispatch(buffer: RpcBuffer): RpcBuffer =
        when (val payload = buffer.payload) {
            is BridgePayload.Request.InjectedEnvVar ->
                RpcBuffer(payload = BridgePayload.Response.StringValue(injectedEnvVar(payload.variable)))
            is BridgePayload.Request.TrackEnvVar -> {
                trackEnvVar(payload.variable, payload.value)
                RpcBuffer(payload = BridgePayload.Response.UnitValue)
            }
            is BridgePayload.Request.TrackPath -> {
                trackPath(payload.path)
                RpcBuffer(payload = BridgePayload.Response.UnitValue)
            }
            is BridgePayload.Request.SpanSourceText ->
                RpcBuffer(payload = BridgePayload.Response.StringValue(spanSourceText(payload.span)))
            is BridgePayload.Request.ExpandExpr ->
                RpcBuffer(payload = BridgePayload.Response.TokenStreamResult(tsExpandExpr(payload.stream)))
            else -> buffer.copy()
        }
}

internal class Dispatcher<S : BridgeServer>(
    val server: S,
    val handleStore: HandleStore<S> = HandleStore.new(),
) {
    fun dispatch(buffer: RpcBuffer): RpcBuffer = server.dispatch(buffer)

    fun globals(): ExpnGlobals<ClientSpan> = server.globals()

    fun dispatchBinary(buf: Buffer): Buffer {
        val reader = buf.toReader()
        val tag = RpcCodec.decodeApiTag(reader)
        return when (tag) {
            ApiTags.InjectedEnvVar ->
                invokeAndEncode(buf, {
                    val variable = RpcCodec.decodeString(reader)
                    server.injectedEnvVar(variable)
                }) { w, res -> RpcCodec.encodeNullable(w, res, RpcCodec::encodeString) }

            ApiTags.TrackEnvVar ->
                invokeAndEncode(buf, {
                    val variable = RpcCodec.decodeString(reader)
                    val value = RpcCodec.decodeNullable(reader, RpcCodec::decodeString)
                    server.trackEnvVar(variable, value)
                }) { _, _ -> RpcCodec.encodeUnit() }

            ApiTags.TrackPath ->
                invokeAndEncode(buf, {
                    val path = RpcCodec.decodeString(reader)
                    server.trackPath(path)
                }) { _, _ -> RpcCodec.encodeUnit() }

            ApiTags.LiteralFromStr ->
                invokeAndEncode(buf, {
                    val s = RpcCodec.decodeString(reader)
                    server.literalFromStr(s)
                }) { w, res ->
                    RpcCodec.encodeResult(w, res) { bufOut, lit ->
                        RpcCodec.encodeLitKind(bufOut, lit.kind)
                        server.withSymbolString(lit.symbol) { symText -> RpcCodec.encodeString(bufOut, symText) }
                        RpcCodec.encodeNullable(bufOut, lit.suffix) { b, suf ->
                            server.withSymbolString(suf) { sufText -> RpcCodec.encodeString(b, sufText) }
                        }
                        handleStore.encodeSpan(bufOut, Marked.mark(lit.span))
                    }
                }

            ApiTags.EmitDiagnostic ->
                invokeAndEncode(buf, {
                    val diagnostic = RpcCodec.decodeDiagnostic(reader) { r -> handleStore.decodeSpan(r).unmark() }
                    server.emitDiagnostic(diagnostic)
                }) { _, _ -> RpcCodec.encodeUnit() }

            ApiTags.TsDrop ->
                invokeAndEncode(buf, {
                    val stream = handleStore.decodeTokenStream(reader).unmark()
                    server.tsDrop(stream)
                }) { _, _ -> RpcCodec.encodeUnit() }

            ApiTags.TsClone ->
                invokeAndEncode(buf, {
                    val stream = handleStore.borrowTokenStream(reader).unmark()
                    server.tsClone(stream)
                }) { w, res -> handleStore.encodeTokenStream(w, Marked.mark(res)) }

            ApiTags.TsIsEmpty ->
                invokeAndEncode(buf, {
                    val stream = handleStore.borrowTokenStream(reader).unmark()
                    server.tsIsEmpty(stream)
                }) { w, res -> RpcCodec.encodeBoolean(w, res) }

            ApiTags.TsExpandExpr ->
                invokeAndEncode(buf, {
                    val stream = handleStore.borrowTokenStream(reader).unmark()
                    server.tsExpandExpr(stream)
                }) { w, res ->
                    RpcCodec.encodeResult(w, res) { bufOut, ts ->
                        handleStore.encodeTokenStream(bufOut, Marked.mark(ts))
                    }
                }

            ApiTags.TsFromStr ->
                invokeAndEncode(buf, {
                    val src = RpcCodec.decodeString(reader)
                    server.tsFromStr(src)
                }) { w, res ->
                    RpcCodec.encodeResult(w, res) { bufOut, ts ->
                        handleStore.encodeTokenStream(bufOut, Marked.mark(ts))
                    }
                }

            ApiTags.TsToString ->
                invokeAndEncode(buf, {
                    val stream = handleStore.borrowTokenStream(reader).unmark()
                    server.tsToString(stream)
                }) { w, res -> RpcCodec.encodeString(w, res) }

            ApiTags.TsFromTokenTree ->
                invokeAndEncode(buf, {
                    val tree = handleStore.decodeTree(reader)
                    server.tsFromTokenTree(tree)
                }) { w, res -> handleStore.encodeTokenStream(w, Marked.mark(res)) }

            ApiTags.TsConcatTrees ->
                invokeAndEncode(buf, {
                    val base = RpcCodec.decodeNullable(reader) { r -> handleStore.decodeTokenStream(r).unmark() }
                    val trees = RpcCodec.decodeList(reader) { r -> handleStore.decodeTree(r) }
                    server.tsConcatTrees(base, trees)
                }) { w, res -> handleStore.encodeTokenStream(w, Marked.mark(res)) }

            ApiTags.TsConcatStreams ->
                invokeAndEncode(buf, {
                    val base = RpcCodec.decodeNullable(reader) { r -> handleStore.decodeTokenStream(r).unmark() }
                    val streams = RpcCodec.decodeList(reader) { r -> handleStore.decodeTokenStream(r).unmark() }
                    server.tsConcatStreams(base, streams)
                }) { w, res -> handleStore.encodeTokenStream(w, Marked.mark(res)) }

            ApiTags.TsIntoTrees ->
                invokeAndEncode(buf, {
                    val stream = handleStore.decodeTokenStream(reader).unmark()
                    server.tsIntoTrees(stream)
                }) { w, res ->
                    RpcCodec.encodeList(w, res) { bufOut, tree -> handleStore.encodeTree(bufOut, tree) }
                }

            ApiTags.SpanDebug ->
                invokeAndEncode(buf, {
                    val span = handleStore.decodeSpan(reader).unmark()
                    server.spanDebug(span)
                }) { w, res -> RpcCodec.encodeString(w, res) }

            ApiTags.SpanParent ->
                invokeAndEncode(buf, {
                    val span = handleStore.decodeSpan(reader).unmark()
                    server.spanParent(span)
                }) { w, res ->
                    RpcCodec.encodeNullable(w, res) { bufOut, sp -> handleStore.encodeSpan(bufOut, Marked.mark(sp)) }
                }

            ApiTags.SpanSource ->
                invokeAndEncode(buf, {
                    val span = handleStore.decodeSpan(reader).unmark()
                    server.spanSource(span)
                }) { w, res -> handleStore.encodeSpan(w, Marked.mark(res)) }

            ApiTags.SpanByteRange ->
                invokeAndEncode(buf, {
                    val span = handleStore.decodeSpan(reader).unmark()
                    server.spanByteRange(span)
                }) { w, res -> RpcCodec.encodeIntRange(w, res) }

            ApiTags.SpanStart ->
                invokeAndEncode(buf, {
                    val span = handleStore.decodeSpan(reader).unmark()
                    server.spanStart(span)
                }) { w, res -> handleStore.encodeSpan(w, Marked.mark(res)) }

            ApiTags.SpanEnd ->
                invokeAndEncode(buf, {
                    val span = handleStore.decodeSpan(reader).unmark()
                    server.spanEnd(span)
                }) { w, res -> handleStore.encodeSpan(w, Marked.mark(res)) }

            ApiTags.SpanLine ->
                invokeAndEncode(buf, {
                    val span = handleStore.decodeSpan(reader).unmark()
                    server.spanLine(span)
                }) { w, res -> RpcCodec.encodeInt(w, res) }

            ApiTags.SpanColumn ->
                invokeAndEncode(buf, {
                    val span = handleStore.decodeSpan(reader).unmark()
                    server.spanColumn(span)
                }) { w, res -> RpcCodec.encodeInt(w, res) }

            ApiTags.SpanFile ->
                invokeAndEncode(buf, {
                    val span = handleStore.decodeSpan(reader).unmark()
                    server.spanFile(span)
                }) { w, res -> RpcCodec.encodeString(w, res) }

            ApiTags.SpanLocalFile ->
                invokeAndEncode(buf, {
                    val span = handleStore.decodeSpan(reader).unmark()
                    server.spanLocalFile(span)
                }) { w, res -> RpcCodec.encodeNullable(w, res, RpcCodec::encodeString) }

            ApiTags.SpanJoin ->
                invokeAndEncode(buf, {
                    val span = handleStore.decodeSpan(reader).unmark()
                    val other = handleStore.decodeSpan(reader).unmark()
                    server.spanJoin(span, other)
                }) { w, res ->
                    RpcCodec.encodeNullable(w, res) { bufOut, sp -> handleStore.encodeSpan(bufOut, Marked.mark(sp)) }
                }

            ApiTags.SpanSubspan ->
                invokeAndEncode(buf, {
                    val span = handleStore.decodeSpan(reader).unmark()
                    val start = RpcCodec.decodeInt(reader)
                    val end = RpcCodec.decodeInt(reader)
                    server.spanSubspan(span, start, end)
                }) { w, res ->
                    RpcCodec.encodeNullable(w, res) { bufOut, sp -> handleStore.encodeSpan(bufOut, Marked.mark(sp)) }
                }

            ApiTags.SpanResolvedAt ->
                invokeAndEncode(buf, {
                    val span = handleStore.decodeSpan(reader).unmark()
                    val at = handleStore.decodeSpan(reader).unmark()
                    server.spanResolvedAt(span, at)
                }) { w, res -> handleStore.encodeSpan(w, Marked.mark(res)) }

            ApiTags.SpanSourceText ->
                invokeAndEncode(buf, {
                    val span = handleStore.decodeSpan(reader).unmark()
                    server.spanSourceText(span)
                }) { w, res -> RpcCodec.encodeNullable(w, res, RpcCodec::encodeString) }

            ApiTags.SpanSaveSpan ->
                invokeAndEncode(buf, {
                    val span = handleStore.decodeSpan(reader).unmark()
                    server.spanSaveSpan(span)
                }) { w, res -> RpcCodec.encodeInt(w, res) }

            ApiTags.SpanRecoverProcMacroSpan ->
                invokeAndEncode(buf, {
                    val id = RpcCodec.decodeInt(reader)
                    server.spanRecoverProcMacroSpan(id)
                }) { w, res -> handleStore.encodeSpan(w, Marked.mark(res)) }

            ApiTags.SymbolNormalizeAndValidateIdent ->
                invokeAndEncode(buf, {
                    val string = RpcCodec.decodeString(reader)
                    server.symbolNormalizeAndValidateIdent(string)
                }) { w, res ->
                    RpcCodec.encodeResult(w, res) { bufOut, sym ->
                        server.withSymbolString(sym) { symText -> RpcCodec.encodeString(bufOut, symText) }
                    }
                }
        }
    }

    private fun <R> invokeAndEncode(
        buf: Buffer,
        callMethod: () -> R,
        encodeOk: (Buffer, R) -> Unit,
    ): Buffer {
        val outcome: PanicOutcome<R> =
            try {
                PanicOutcome.Ok(callMethod())
            } catch (t: Throwable) {
                PanicOutcome.Panicked(PanicMessage.from(t))
            }
        buf.clear()
        RpcCodec.encodePanicOutcome(buf, outcome, encodeOk)
        return buf
    }
}

private class SameThreadFlagHolder {
    var running: Boolean = false
}

private class SameThreadLocal {
    private val local: ThreadLocal<SameThreadFlagHolder> = ThreadLocal()

    fun get(): Boolean = local.getOr { SameThreadFlagHolder() }.running

    fun replace(next: Boolean): Boolean {
        val holder = local.getOr { SameThreadFlagHolder() }
        val prev = holder.running
        holder.running = next
        return prev
    }

    fun set(next: Boolean) {
        local.getOr { SameThreadFlagHolder() }.running = next
    }
}

private val alreadyRunningSameThread = SameThreadLocal()

/**
 * Guard keeping the thread-local same-thread execution flag set so nested
 * same-thread procedural macro reentrance is detected immediately.
 */
internal class RunningSameThreadGuard private constructor() {
    fun drop() {
        alreadyRunningSameThread.set(false)
    }

    companion object {
        fun new(): RunningSameThreadGuard {
            val alreadyRunning = alreadyRunningSameThread.replace(true)
            check(!alreadyRunning) {
                "same-thread nesting (\"reentrance\") of proc macro executions is not supported"
            }
            return RunningSameThreadGuard()
        }
    }
}

internal interface ExecutionStrategy {
    fun runBridgeAndClient(
        dispatcher: Dispatcher<out BridgeServer>,
        input: RpcBuffer,
        runClient: (BridgeConfig) -> RpcBuffer,
        forceShowPanics: Boolean,
    ): RpcBuffer

    fun runBridgeAndClientBinary(
        dispatcher: Dispatcher<out BridgeServer>,
        input: Buffer,
        runClient: ClientRunCallback,
        forceShowPanics: Boolean,
    ): Buffer {
        val wrapped =
            runBridgeAndClient(
                dispatcher = dispatcher,
                input = RpcBuffer.fromBuffer(input),
                runClient = { config ->
                    val binaryOut =
                        runClient.invoke(
                            config.copy(
                                binaryInput = input,
                                binaryDispatch = Closure.from { b -> dispatcher.dispatchBinary(b) },
                            ),
                        )
                    RpcBuffer.fromBuffer(binaryOut)
                },
                forceShowPanics = forceShowPanics,
            )
        return wrapped.toBuffer()
    }
}

internal data class MaybeCrossThread(
    val crossThread: Boolean,
) : ExecutionStrategy {
    override fun runBridgeAndClient(
        dispatcher: Dispatcher<out BridgeServer>,
        input: RpcBuffer,
        runClient: (BridgeConfig) -> RpcBuffer,
        forceShowPanics: Boolean,
    ): RpcBuffer {
        if (crossThread) {
            throw UnsupportedOperationException("CrossThread bridge execution requires a platform-specific runner")
        }
        val config =
            BridgeConfig(
                input = input,
                dispatch = BridgeDispatch { dispatcher.dispatch(it) },
                forceShowPanics = forceShowPanics,
                globals = dispatcher.globals(),
            )
        return BridgeClientState.enter(
            BridgeState(
                globals = config.globals,
                dispatch = config.dispatch,
            ),
        ) {
            runClient(config)
        }
    }

    override fun runBridgeAndClientBinary(
        dispatcher: Dispatcher<out BridgeServer>,
        input: Buffer,
        runClient: ClientRunCallback,
        forceShowPanics: Boolean,
    ): Buffer {
        if (crossThread) {
            throw UnsupportedOperationException("CrossThread bridge execution requires a platform-specific runner")
        }
        val guard = RunningSameThreadGuard.new()
        return try {
            val closure = Closure.from { buf -> dispatcher.dispatchBinary(buf) }
            val config =
                BridgeConfig(
                    input = RpcBuffer(),
                    dispatch = BridgeDispatch { dispatcher.dispatch(it) },
                    forceShowPanics = forceShowPanics,
                    globals = dispatcher.globals(),
                    binaryInput = input,
                    binaryDispatch = closure,
                )
            runClient.invoke(config)
        } finally {
            guard.drop()
        }
    }
}

internal val SameThread: MaybeCrossThread = MaybeCrossThread(crossThread = false)

internal val CrossThread: MaybeCrossThread = MaybeCrossThread(crossThread = true)

private class PipeChannel<T> {
    val queue: ArrayDeque<T> = ArrayDeque()
    var senderOpen: Boolean = true
    var receiverOpen: Boolean = true
}

/**
 * Paired bidirectional message pipe between server and client endpoints.
 */
internal class MessagePipe<T> private constructor(
    private val outbound: PipeChannel<T>,
    private val inbound: PipeChannel<T>,
) {
    /** Sends [value] to the peer endpoint of this pipe. */
    fun send(value: T) {
        check(outbound.receiverOpen) { "message pipe peer receiver has been closed" }
        outbound.queue.addLast(value)
    }

    /**
     * Receives the next message from the peer endpoint, or returns `null` once
     * the peer endpoint has been closed and all queued messages are drained.
     */
    fun recv(): T? {
        if (inbound.queue.isNotEmpty()) {
            return inbound.queue.removeFirst()
        }
        check(!inbound.senderOpen) { "message pipe has no queued message while peer remains open" }
        return null
    }

    /** Marks this endpoint closed so its peer observes end-of-stream on [recv]. */
    fun close() {
        outbound.senderOpen = false
        inbound.receiverOpen = false
    }

    companion object {
        /** Creates a connected pair of [MessagePipe] endpoints. */
        fun <T> new(): Pair<MessagePipe<T>, MessagePipe<T>> {
            val firstToSecond = PipeChannel<T>()
            val secondToFirst = PipeChannel<T>()
            return Pair(
                MessagePipe(outbound = firstToSecond, inbound = secondToFirst),
                MessagePipe(outbound = secondToFirst, inbound = firstToSecond),
            )
        }
    }
}

internal fun <S : BridgeServer, I, O> runServer(
    strategy: ExecutionStrategy,
    handleCounters: HandleCounters,
    server: S,
    input: I,
    encodeInput: (Buffer, HandleStore<S>, I) -> Unit,
    decodeOutput: (BufferReader, HandleStore<S>) -> O,
    runClient: ClientRunCallback,
    forceShowPanics: Boolean,
): PanicOutcome<O> {
    val dispatcher = Dispatcher(server = server, handleStore = HandleStore.new(handleCounters))
    val globals = dispatcher.server.globals()
    var buf = Buffer.new()
    RpcCodec.encodeExpnGlobals(buf, globals) { w, sp ->
        dispatcher.handleStore.encodeSpan(w, Marked.mark(sp))
    }
    encodeInput(buf, dispatcher.handleStore, input)
    buf = strategy.runBridgeAndClientBinary(dispatcher, buf, runClient, forceShowPanics)
    return RpcCodec.decodePanicOutcome(buf.toReader()) { r ->
        decodeOutput(r, dispatcher.handleStore)
    }
}

internal fun <S : BridgeServer> Client<ClientTokenStream, ClientTokenStream>.run(
    strategy: ExecutionStrategy,
    server: S,
    input: ClientTokenStream,
    forceShowPanics: Boolean,
): PanicOutcome<ClientTokenStream> =
    runServer(
        strategy = strategy,
        handleCounters = handleCounters,
        server = server,
        input = Marked.mark<ClientTokenStream, ClientTokenStream>(input),
        encodeInput = { w, store, marked -> store.encodeTokenStream(w, marked) },
        decodeOutput = { r, store ->
            RpcCodec.decodeNullable(r) { reader -> store.decodeTokenStream(reader) }
        },
        runClient = run,
        forceShowPanics = forceShowPanics,
    ).map { maybeStream ->
        maybeStream?.unmark() ?: ClientTokenStream(TokenStream.new())
    }

internal fun <S : BridgeServer> Client<Pair<ClientTokenStream, ClientTokenStream>, ClientTokenStream>.run(
    strategy: ExecutionStrategy,
    server: S,
    input: ClientTokenStream,
    input2: ClientTokenStream,
    forceShowPanics: Boolean,
): PanicOutcome<ClientTokenStream> =
    runServer(
        strategy = strategy,
        handleCounters = handleCounters,
        server = server,
        input =
            Pair(
                Marked.mark<ClientTokenStream, ClientTokenStream>(input),
                Marked.mark<ClientTokenStream, ClientTokenStream>(input2),
            ),
        encodeInput = { w, store, (first, second) ->
            store.encodeTokenStream(w, first)
            store.encodeTokenStream(w, second)
        },
        decodeOutput = { r, store ->
            RpcCodec.decodeNullable(r) { reader -> store.decodeTokenStream(reader) }
        },
        runClient = run,
        forceShowPanics = forceShowPanics,
    ).map { maybeStream ->
        maybeStream?.unmark() ?: ClientTokenStream(TokenStream.new())
    }
