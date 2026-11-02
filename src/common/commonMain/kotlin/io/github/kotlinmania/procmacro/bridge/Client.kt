// port-lint: source src/bridge/client.rs
package io.github.kotlinmania.procmacro.bridge

import io.github.kotlinmania.procmacro.DelimSpanData
import io.github.kotlinmania.procmacro.Diagnostic
import io.github.kotlinmania.procmacro.ExpandError
import io.github.kotlinmania.procmacro.Group
import io.github.kotlinmania.procmacro.GroupData
import io.github.kotlinmania.procmacro.Ident
import io.github.kotlinmania.procmacro.IdentData
import io.github.kotlinmania.procmacro.Literal
import io.github.kotlinmania.procmacro.LiteralData
import io.github.kotlinmania.procmacro.MultiSpan
import io.github.kotlinmania.procmacro.Punct
import io.github.kotlinmania.procmacro.PunctData
import io.github.kotlinmania.procmacro.Spacing
import io.github.kotlinmania.procmacro.Span
import io.github.kotlinmania.procmacro.SpanData
import io.github.kotlinmania.procmacro.SpanList
import io.github.kotlinmania.procmacro.SpanRegistry
import io.github.kotlinmania.procmacro.TokenStream
import io.github.kotlinmania.procmacro.TokenStreamData
import io.github.kotlinmania.procmacro.TokenStreamParseOutcome
import io.github.kotlinmania.procmacro.TokenTree
import io.github.kotlinmania.rustcliteralescaper.EscapeError
import io.github.kotlinmania.rustcliteralescaper.checkRawByteStr
import io.github.kotlinmania.rustcliteralescaper.checkRawCStr
import io.github.kotlinmania.rustcliteralescaper.checkRawStr
import io.github.kotlinmania.threadlocal.ThreadLocal

/** Single-argument procedural macro expansion callback. */
public fun interface BangMacroCallback {
    public fun expand(input: TokenStream): TokenStream
}

/** Two-argument attribute procedural macro expansion callback. */
public fun interface AttrMacroCallback {
    public fun expand(
        attr: TokenStream,
        item: TokenStream,
    ): TokenStream
}

private object DefaultBridgeServer : BridgeServer

internal fun interface ClientRunCallback {
    fun invoke(config: BridgeConfig): Buffer
}

internal class HandleCounters(
    val tokenStream: HandleCounter = HandleCounter(1),
    val span: HandleCounter = HandleCounter(1),
)

internal val COUNTERS: HandleCounters = HandleCounters()

internal data class ClientTokenStream(
    val stream: TokenStream,
) {
    fun drop() {
        BridgeMethods.tsDrop(this)
    }

    fun clone(): ClientTokenStream = BridgeMethods.tsClone(this)
}

internal data class ClientSpan(
    val span: Span,
) {
    companion object {
        fun defSite(): ClientSpan = BridgeClientState.withState().globals.defSite

        fun callSite(): ClientSpan = BridgeClientState.withState().globals.callSite

        fun mixedSite(): ClientSpan = BridgeClientState.withState().globals.mixedSite
    }
}

private class BridgeStateStack {
    val states: MutableList<BridgeState> = mutableListOf()
    var inUse: Boolean = false
    var suspendedDepth: Int = 0
}

private class BridgeStateLocal {
    private val stacks: ThreadLocal<BridgeStateStack> = ThreadLocal()

    fun holder(): BridgeStateStack = stacks.getOr { BridgeStateStack() }

    fun stack(): MutableList<BridgeState> = holder().states
}

private class RestoreOnDrop(
    private val stack: MutableList<BridgeState>,
    private val state: BridgeState,
) {
    fun drop() {
        if (stack.lastOrNull() === state) {
            stack.removeAt(stack.lastIndex)
        } else {
            stack.remove(state)
        }
    }
}

public object BridgeClientState {
    private val stateLocal = BridgeStateLocal()

    internal fun <R> set(
        state: BridgeState,
        block: () -> R,
    ): R {
        val stack = stateLocal.stack()
        stack.add(state)
        val restore = RestoreOnDrop(stack, state)
        return try {
            block()
        } finally {
            restore.drop()
        }
    }

    internal fun enter(
        state: BridgeState,
        block: () -> RpcBuffer,
    ): RpcBuffer = set(state, block)

    public fun isAvailable(): Boolean = currentOrNull() != null

    public fun runBangMacro(
        name: String,
        input: TokenStream,
        callback: BangMacroCallback,
    ): TokenStream {
        val macro = ProcMacro.Bang(name = name, client = Client.expand1 { stream -> callback.expand(stream) })
        val outcome =
            macro.client.run(
                strategy = SameThread,
                server = DefaultBridgeServer,
                input = ClientTokenStream(input),
                forceShowPanics = true,
            )
        return outcome.unwrapOrThrow().stream
    }

    public fun runAttrMacro(
        name: String,
        attr: TokenStream,
        item: TokenStream,
        callback: AttrMacroCallback,
    ): TokenStream {
        val macro =
            ProcMacro.Attr(
                name = name,
                client = Client.expand2 { attrStream, itemStream -> callback.expand(attrStream, itemStream) },
            )
        val outcome =
            macro.client.run(
                strategy = SameThread,
                server = DefaultBridgeServer,
                input = ClientTokenStream(attr),
                input2 = ClientTokenStream(item),
                forceShowPanics = true,
            )
        return outcome.unwrapOrThrow().stream
    }

    public fun runCustomDeriveMacro(
        traitName: String,
        attributes: List<String>,
        input: TokenStream,
        callback: BangMacroCallback,
    ): TokenStream {
        val macro =
            ProcMacro.CustomDerive(
                traitName = traitName,
                attributes = attributes,
                client = Client.expand1 { stream -> callback.expand(stream) },
            )
        val outcome =
            macro.client.run(
                strategy = SameThread,
                server = DefaultBridgeServer,
                input = ClientTokenStream(input),
                forceShowPanics = true,
            )
        return outcome.unwrapOrThrow().stream
    }

    internal fun currentOrNull(): BridgeState? {
        val holder = stateLocal.holder()
        if (holder.suspendedDepth > 0) return null
        return holder.states.lastOrNull()
    }

    internal fun <R> withoutCurrentState(block: () -> R): R {
        val holder = stateLocal.holder()
        holder.suspendedDepth += 1
        return try {
            block()
        } finally {
            holder.suspendedDepth -= 1
        }
    }

    internal fun <R> with(block: (BridgeState?) -> R): R = block(currentOrNull())

    internal fun <R> withActive(block: (BridgeState) -> R): R {
        val holder = stateLocal.holder()
        val active =
            holder.states.lastOrNull()
                ?: error("procedural macro API is used outside of a procedural macro")
        check(!holder.inUse) { "procedural macro API is used while it is already in use" }
        holder.inUse = true
        return try {
            block(active)
        } finally {
            holder.inUse = false
        }
    }

    internal fun withState(): BridgeState =
        currentOrNull() ?: BridgeState(
            globals = defaultClientGlobals(),
            dispatch = BridgeDispatch { it },
        )
}

internal data class BridgeState(
    val globals: ExpnGlobals<ClientSpan>,
    val dispatch: BridgeDispatch,
    val cachedBuffer: RpcBuffer = RpcBuffer(),
    val binaryDispatch: Closure? = null,
    var cachedBinaryBuffer: Buffer = Buffer.new(),
    val spanHandles: MutableMap<SpanData, Handle> = linkedMapOf(),
    val handlesToSpan: MutableMap<Handle, Span> = linkedMapOf(),
) {
    fun <R> callBinary(
        tag: ApiTags,
        encodeArgs: (Buffer) -> Unit,
        decodeResult: (BufferReader) -> R,
    ): R {
        val dispatcher = checkNotNull(binaryDispatch) { "binary bridge dispatch is not configured" }
        var buf = cachedBinaryBuffer.take()
        buf.clear()
        RpcCodec.encodeApiTag(buf, tag)
        encodeArgs(buf)
        buf =
            BridgeClientState.withoutCurrentState {
                dispatcher.call(buf)
            }
        val outcome = RpcCodec.decodePanicOutcome(buf.toReader(), decodeResult)
        cachedBinaryBuffer = buf
        return outcome.unwrapOrThrow()
    }

    fun registerSpanHandle(
        handle: Handle,
        span: Span,
    ) {
        handlesToSpan[handle] = span
        spanHandles[span.data] = handle
    }

    fun encodeSpanHandle(
        w: Buffer,
        span: ClientSpan,
    ) {
        val existing = spanHandles[span.span.data]
        val handle =
            if (existing != null) {
                existing
            } else {
                val savedId = SpanRegistry.save(span.span)
                val allocated =
                    callBinary(
                        ApiTags.SpanRecoverProcMacroSpan,
                        encodeArgs = { RpcCodec.encodeInt(it, savedId) },
                        decodeResult = { Handle.decode(it) },
                    )
                registerSpanHandle(allocated, span.span)
                allocated
            }
        handle.encode(w)
    }

    fun decodeSpanHandle(r: BufferReader): ClientSpan {
        val handle = Handle.decode(r)
        val existing = handlesToSpan[handle]
        if (existing != null) {
            return ClientSpan(existing)
        }
        val savedId =
            callBinary(
                ApiTags.SpanSaveSpan,
                encodeArgs = { handle.encode(it) },
                decodeResult = { RpcCodec.decodeInt(it) },
            )
        val recovered = SpanRegistry.recover(savedId)
        registerSpanHandle(handle, recovered)
        return ClientSpan(recovered)
    }

    fun materializeTokenStream(handle: Handle): TokenStream {
        val wireTrees =
            callBinary(
                ApiTags.TsIntoTrees,
                encodeArgs = { handle.encode(it) },
                decodeResult = { r ->
                    RpcCodec.decodeList(r) { reader ->
                        RpcCodec.decodeTokenTree(
                            reader,
                            decodeStream = { Handle.decode(it) },
                            decodeSpan = { decodeSpanHandle(it) },
                        )
                    }
                },
            )
        val publicTrees = wireTrees.map { materializeTree(it) }
        return TokenStream(TokenStreamData(publicTrees))
    }

    private fun materializeTree(tree: BridgeTokenTree<Handle, ClientSpan>): TokenTree =
        when (tree) {
            is BridgeTokenTree.Group -> {
                val groupSpan = tree.value.span
                TokenTree.Group(
                    Group(
                        GroupData(
                            delimiter = tree.value.delimiter,
                            stream = materializeTokenStream(tree.value.stream),
                            span =
                                DelimSpanData(
                                    open = groupSpan.open.span,
                                    close = groupSpan.close.span,
                                    entire = groupSpan.entire.span,
                                ),
                        ),
                    ),
                )
            }
            is BridgeTokenTree.Punct ->
                TokenTree.Punct(
                    Punct(
                        PunctData(
                            ch = tree.value.ch,
                            joint = tree.value.spacing == Spacing.JOINT,
                            span = tree.value.span.span,
                        ),
                    ),
                )
            is BridgeTokenTree.Ident ->
                TokenTree.Ident(
                    Ident(
                        IdentData(
                            sym = tree.value.sym.asString(),
                            isRaw = tree.value.isRaw,
                            span = tree.value.span.span,
                        ),
                    ),
                )
            is BridgeTokenTree.Literal ->
                TokenTree.Literal(
                    Literal(
                        LiteralData(
                            kind = tree.value.kind.toPublicLitKind(),
                            symbol = tree.value.symbol.asString(),
                            suffix = tree.value.suffix?.asString(),
                            span = tree.value.span.span,
                        ),
                    ),
                )
        }

    fun allocateServerTokenStream(stream: TokenStream): Handle? {
        if (stream.isEmpty()) return null
        return allocateServerStreamHandle(stream)
    }

    fun allocateServerStreamHandle(stream: TokenStream): Handle {
        val wireTrees = stream.data.trees.map { allocateWireTree(it) }
        return callBinary(
            ApiTags.TsConcatTrees,
            encodeArgs = { w ->
                RpcCodec.encodeNullable<Handle>(w, null) { buf, h -> h.encode(buf) }
                RpcCodec.encodeList(w, wireTrees) { buf, wireTree ->
                    RpcCodec.encodeTokenTree(
                        buf,
                        wireTree,
                        encodeStream = { b, h -> h.encode(b) },
                        encodeSpan = { b, sp -> encodeSpanHandle(b, sp) },
                    )
                }
            },
            decodeResult = { Handle.decode(it) },
        )
    }

    private fun allocateWireTree(tree: TokenTree): BridgeTokenTree<Handle, ClientSpan> =
        when (tree) {
            is TokenTree.Group ->
                BridgeTokenTree.Group(
                    BridgeGroup(
                        delimiter = tree.value.delimiter(),
                        stream = allocateServerStreamHandle(tree.value.stream()),
                        span =
                            DelimSpan(
                                open = ClientSpan(tree.value.spanOpen()),
                                close = ClientSpan(tree.value.spanClose()),
                                entire = ClientSpan(tree.value.span()),
                            ),
                    ),
                )
            is TokenTree.Punct ->
                BridgeTokenTree.Punct(
                    BridgePunct(
                        ch = tree.value.asChar(),
                        spacing = tree.value.spacing(),
                        span = ClientSpan(tree.value.span()),
                    ),
                )
            is TokenTree.Ident ->
                BridgeTokenTree.Ident(
                    BridgeIdent(
                        sym = Symbol.intern(tree.value.data.sym),
                        isRaw = tree.value.data.isRaw,
                        span = ClientSpan(tree.value.span()),
                    ),
                )
            is TokenTree.Literal -> {
                val litData = tree.value.data
                BridgeTokenTree.Literal(
                    BridgeLiteral(
                        kind = litData.kind.toBridgeLitKind(),
                        symbol = Symbol.intern(litData.symbol),
                        suffix = litData.suffix?.let(Symbol::intern),
                        span = ClientSpan(tree.value.span()),
                    ),
                )
            }
        }
}

internal object BridgeMethods {
    fun injectedEnvVar(variable: String): String? {
        val state = BridgeClientState.currentOrNull()
        if (state?.binaryDispatch != null) {
            return state.callBinary(
                ApiTags.InjectedEnvVar,
                encodeArgs = { RpcCodec.encodeString(it, variable) },
                decodeResult = { r -> RpcCodec.decodeNullable(r, RpcCodec::decodeString) },
            )
        }
        return state
            ?.dispatchRequest(BridgePayload.Request.InjectedEnvVar(variable))
            .stringValueOrNull()
    }

    fun trackEnvVar(
        variable: String,
        value: String?,
    ) {
        TrackedInputs.trackEnvVar(variable, value)
        val state = BridgeClientState.currentOrNull()
        if (state?.binaryDispatch != null) {
            state.callBinary(
                ApiTags.TrackEnvVar,
                encodeArgs = { w ->
                    RpcCodec.encodeString(w, variable)
                    RpcCodec.encodeNullable(w, value, RpcCodec::encodeString)
                },
                decodeResult = { RpcCodec.decodeUnit() },
            )
            return
        }
        state?.dispatchRequest(BridgePayload.Request.TrackEnvVar(variable, value))
    }

    fun trackPath(path: String) {
        TrackedInputs.trackPath(path)
        val state = BridgeClientState.currentOrNull()
        if (state?.binaryDispatch != null) {
            state.callBinary(
                ApiTags.TrackPath,
                encodeArgs = { RpcCodec.encodeString(it, path) },
                decodeResult = { RpcCodec.decodeUnit() },
            )
            return
        }
        state?.dispatchRequest(BridgePayload.Request.TrackPath(path))
    }

    fun literalFromStr(source: String): Result<BridgeLiteral<ClientSpan>> {
        val state = BridgeClientState.currentOrNull()
        if (state?.binaryDispatch != null) {
            return state.callBinary(
                ApiTags.LiteralFromStr,
                encodeArgs = { RpcCodec.encodeString(it, source) },
                decodeResult = { r ->
                    RpcCodec.decodeResult(r) { reader ->
                        BridgeLiteral(
                            kind = RpcCodec.decodeLitKind(reader),
                            symbol = Symbol.decode(reader),
                            suffix = RpcCodec.decodeNullable(reader, Symbol::decode),
                            span = state.decodeSpanHandle(reader),
                        )
                    }
                },
            )
        }
        return parseRustLiteral(source)
            ?: when (val parsed = TokenStream.fromString(source)) {
                is TokenStreamParseOutcome.Ok -> {
                    val literal =
                        parsed
                            .value
                            .data
                            .trees
                            .singleOrNull()
                    when (literal) {
                        is TokenTree.Literal ->
                            when (val bridgeTree = literal.toBridgeTree()) {
                                is BridgeTokenTree.Literal -> Result.Ok(bridgeTree.value)
                                else -> Result.Err("expected one literal token")
                            }
                        else -> Result.Err("expected one literal token")
                    }
                }
                is TokenStreamParseOutcome.Err -> Result.Err(parsed.error.toString())
            }
    }

    fun emitDiagnostic(diagnostic: BridgeDiagnostic<ClientSpan>) {
        val state = BridgeClientState.currentOrNull()
        if (state?.binaryDispatch != null) {
            state.callBinary(
                ApiTags.EmitDiagnostic,
                encodeArgs = { w ->
                    RpcCodec.encodeDiagnostic(w, diagnostic) { buf, sp -> state.encodeSpanHandle(buf, sp) }
                },
                decodeResult = { RpcCodec.decodeUnit() },
            )
            return
        }
        diagnostic.toPublicDiagnostic().emitLocally()
    }

    fun tsDrop(stream: ClientTokenStream) {
        stream.stream.data.trees = emptyList()
    }

    fun tsClone(stream: ClientTokenStream): ClientTokenStream =
        ClientTokenStream(
            TokenStream(
                TokenStreamData(
                    stream.stream.data.trees
                        .map { it.deepClone() },
                ),
            ),
        )

    fun tsIsEmpty(stream: ClientTokenStream): Boolean = stream.stream.isEmpty()

    fun tsExpandExpr(stream: ClientTokenStream): Result<ClientTokenStream> {
        val state = BridgeClientState.currentOrNull()
        if (state?.binaryDispatch != null) {
            val handle = state.allocateServerStreamHandle(stream.stream)
            val resultHandles =
                state.callBinary(
                    ApiTags.TsExpandExpr,
                    encodeArgs = { handle.encode(it) },
                    decodeResult = { r -> RpcCodec.decodeResult(r, Handle::decode) },
                )
            state.callBinary(
                ApiTags.TsDrop,
                encodeArgs = { handle.encode(it) },
                decodeResult = { RpcCodec.decodeUnit() },
            )
            return resultHandles.map { ClientTokenStream(state.materializeTokenStream(it)) }
        }
        return when (
            val response =
                state?.dispatchRequest(BridgePayload.Request.ExpandExpr(stream))
        ) {
            is BridgePayload.Response.TokenStreamResult -> response.result
            else -> stream.expandExprLocally()
        }
    }

    fun tsFromStr(source: String): Result<ClientTokenStream> =
        when (val parsed = TokenStream.fromString(source)) {
            is TokenStreamParseOutcome.Ok -> Result.Ok(ClientTokenStream(parsed.value))
            is TokenStreamParseOutcome.Err -> Result.Err(parsed.error.toString())
        }

    fun tsToString(stream: ClientTokenStream): String = stream.stream.toString()

    fun tsFromTokenTree(tree: BridgeTokenTree<ClientTokenStream, ClientSpan>): ClientTokenStream =
        ClientTokenStream(TokenStream.fromTokenTree(tree.toPublicTree()))

    fun tsConcatTrees(
        base: ClientTokenStream?,
        trees: List<BridgeTokenTree<ClientTokenStream, ClientSpan>>,
    ): ClientTokenStream {
        val out = base?.stream ?: TokenStream.new()
        out.extendTokenTrees(trees.map { it.toPublicTree() })
        return ClientTokenStream(out)
    }

    fun tsConcatStreams(
        base: ClientTokenStream?,
        streams: List<ClientTokenStream>,
    ): ClientTokenStream {
        val out = base?.stream ?: TokenStream.new()
        out.extendTokenStreams(streams.map { it.stream })
        return ClientTokenStream(out)
    }

    fun tsIntoTrees(stream: ClientTokenStream): List<BridgeTokenTree<ClientTokenStream, ClientSpan>> =
        stream
            .stream
            .data
            .trees
            .map { it.toBridgeTree() }

    fun spanDebug(span: ClientSpan): String = span.span.toString()

    fun spanParent(span: ClientSpan): ClientSpan? = span.span.parent()?.let(::ClientSpan)

    fun spanSource(span: ClientSpan): ClientSpan = ClientSpan(span.span.source())

    fun spanByteRange(span: ClientSpan): IntRange = span.span.byteRange()

    fun spanStart(span: ClientSpan): ClientSpan = ClientSpan(span.span.start())

    fun spanEnd(span: ClientSpan): ClientSpan = ClientSpan(span.span.end())

    fun spanLine(span: ClientSpan): Int = span.span.line()

    fun spanColumn(span: ClientSpan): Int = span.span.column()

    fun spanFile(span: ClientSpan): String = span.span.file()

    fun spanLocalFile(span: ClientSpan): String? = span.span.localFile()

    fun spanJoin(
        span: ClientSpan,
        other: ClientSpan,
    ): ClientSpan? = span.span.join(other.span)?.let(::ClientSpan)

    fun spanSubspan(
        span: ClientSpan,
        start: Int,
        end: Int,
    ): ClientSpan? {
        val synthetic = span.span.data as? SpanData.Synthetic ?: return null
        val range = synthetic.range
        val length = if (range.isEmpty()) 0 else range.last - range.first + 1
        if (start < 0 || end > length || start > end) return null
        return ClientSpan(Span(SpanData.Synthetic(range.first + start until range.first + end)))
    }

    fun spanResolvedAt(
        span: ClientSpan,
        at: ClientSpan,
    ): ClientSpan = ClientSpan(span.span.resolvedAt(at.span))

    fun spanSourceText(span: ClientSpan): String? {
        val state = BridgeClientState.currentOrNull()
        if (state?.binaryDispatch != null) {
            return state.callBinary(
                ApiTags.SpanSourceText,
                encodeArgs = { state.encodeSpanHandle(it, span) },
                decodeResult = { r -> RpcCodec.decodeNullable(r, RpcCodec::decodeString) },
            )
        }
        return state
            ?.dispatchRequest(BridgePayload.Request.SpanSourceText(span))
            .stringValueOrNull()
            ?: span.span.localSourceText()
    }

    fun spanSaveSpan(span: ClientSpan): Int = span.span.saveSpan()

    fun spanRecoverProcMacroSpan(id: Int): ClientSpan = ClientSpan(SpanRegistry.recover(id))

    fun symbolNormalizeAndValidateIdent(string: String): Result<Symbol> {
        val state = BridgeClientState.currentOrNull()
        if (state?.binaryDispatch != null) {
            return state.callBinary(
                ApiTags.SymbolNormalizeAndValidateIdent,
                encodeArgs = { RpcCodec.encodeString(it, string) },
                decodeResult = { r -> RpcCodec.decodeResult(r, Symbol::decode) },
            )
        }
        return Symbol.normalizeAndValidateIdent(string)
    }
}

/**
 * Client-side RPC entry point carrying phantom input and output markers over
 * a uniform binary buffer callback.
 */
internal class Client<I, O>(
    val handleCounters: HandleCounters,
    val run: ClientRunCallback,
) {
    fun clone(): Client<I, O> = Client(handleCounters = handleCounters, run = run)

    companion object {
        fun expand1(f: (TokenStream) -> TokenStream): Client<ClientTokenStream, ClientTokenStream> {
            val reified =
                reifyToExternCFnHrtBridge<BridgeConfig, Buffer> { config ->
                    runClient(
                        config = config,
                        decodeInput = { reader, state ->
                            val inputHandle = Handle.decode(reader)
                            state.materializeTokenStream(inputHandle)
                        },
                        encodeOutput = { w, output, state ->
                            val outHandle = state.allocateServerTokenStream(output)
                            RpcCodec.encodeNullable(w, outHandle) { buf, h -> h.encode(buf) }
                        },
                        f = f,
                    )
                }
            return Client(handleCounters = COUNTERS, run = ClientRunCallback { reified(it) })
        }

        fun expand2(
            f: (TokenStream, TokenStream) -> TokenStream,
        ): Client<Pair<ClientTokenStream, ClientTokenStream>, ClientTokenStream> {
            val reified =
                reifyToExternCFnHrtBridge<BridgeConfig, Buffer> { config ->
                    runClient(
                        config = config,
                        decodeInput = { reader, state ->
                            val firstHandle = Handle.decode(reader)
                            val secondHandle = Handle.decode(reader)
                            Pair(
                                state.materializeTokenStream(firstHandle),
                                state.materializeTokenStream(secondHandle),
                            )
                        },
                        encodeOutput = { w, output, state ->
                            val outHandle = state.allocateServerTokenStream(output)
                            RpcCodec.encodeNullable(w, outHandle) { buf, h -> h.encode(buf) }
                        },
                        f = { (attr, item) -> f(attr, item) },
                    )
                }
            return Client(handleCounters = COUNTERS, run = ClientRunCallback { reified(it) })
        }
    }
}

private var panicHookInstalled: Boolean = false

internal fun maybeInstallPanicHook(forceShowPanics: Boolean) {
    if (!panicHookInstalled && !forceShowPanics) {
        panicHookInstalled = true
    }
}

/**
 * Client-side execution helper that resets the symbol interner, decodes initial
 * globals and inputs, enters [BridgeClientState], catches panics, and encodes
 * the resulting [PanicOutcome].
 */
internal fun <A, R> runClient(
    config: BridgeConfig,
    decodeInput: (BufferReader, BridgeState) -> A,
    encodeOutput: (Buffer, R, BridgeState) -> Unit,
    f: (A) -> R,
): Buffer {
    var buf = config.binaryInput
    val binaryDispatch = checkNotNull(config.binaryDispatch) { "runClient requires binaryDispatch" }
    try {
        maybeInstallPanicHook(config.forceShowPanics)
        Symbol.invalidateAll()
        val reader = buf.toReader()
        val defSiteHandle = Handle.decode(reader)
        val callSiteHandle = Handle.decode(reader)
        val mixedSiteHandle = Handle.decode(reader)

        val bootstrapState =
            BridgeState(
                globals = config.globals,
                dispatch = config.dispatch,
                binaryDispatch = binaryDispatch,
                cachedBinaryBuffer = Buffer.new(),
            )
        val defSite = bootstrapState.decodeSpanHandle(Buffer.new().also { defSiteHandle.encode(it) }.toReader())
        val callSite = bootstrapState.decodeSpanHandle(Buffer.new().also { callSiteHandle.encode(it) }.toReader())
        val mixedSite = bootstrapState.decodeSpanHandle(Buffer.new().also { mixedSiteHandle.encode(it) }.toReader())

        val state =
            bootstrapState.copy(
                globals = ExpnGlobals(defSite = defSite, callSite = callSite, mixedSite = mixedSite),
            )
        val input = decodeInput(reader, state)
        state.cachedBinaryBuffer = buf.take()

        var encodedSuccess: ByteArray? = null
        BridgeClientState.set(state) {
            val output = f(input)
            val outBuf = Buffer.new()
            RpcCodec.encodePanicOutcome(outBuf, PanicOutcome.Ok(output)) { w, value ->
                encodeOutput(w, value, state)
            }
            encodedSuccess = outBuf.toByteArray()
        }
        buf = state.cachedBinaryBuffer
        buf.clear()
        buf.extendFromArray(checkNotNull(encodedSuccess))
    } catch (t: Throwable) {
        val panic = PanicMessage.from(t)
        buf.clear()
        RpcCodec.encodePanicOutcome<Unit>(buf, PanicOutcome.Panicked(panic)) { _, _ -> }
    }
    Symbol.invalidateAll()
    return buf
}

/**
 * Descriptor for a procedural macro exported by a proc-macro crate.
 */
internal sealed class ProcMacro {
    data class CustomDerive(
        val traitName: String,
        val attributes: List<String>,
        val client: Client<ClientTokenStream, ClientTokenStream>,
    ) : ProcMacro()

    data class Attr(
        val name: String,
        val client: Client<Pair<ClientTokenStream, ClientTokenStream>, ClientTokenStream>,
    ) : ProcMacro()

    data class Bang(
        val name: String,
        val client: Client<ClientTokenStream, ClientTokenStream>,
    ) : ProcMacro()

    fun name(): String =
        when (this) {
            is CustomDerive -> traitName
            is Attr -> name
            is Bang -> name
        }

    companion object {
        fun customDerive(
            traitName: String,
            attributes: List<String>,
            expand: (TokenStream) -> TokenStream,
        ): ProcMacro = CustomDerive(traitName = traitName, attributes = attributes, client = Client.expand1(expand))

        fun attr(
            name: String,
            expand: (TokenStream, TokenStream) -> TokenStream,
        ): ProcMacro = Attr(name = name, client = Client.expand2(expand))

        fun bang(
            name: String,
            expand: (TokenStream) -> TokenStream,
        ): ProcMacro = Bang(name = name, client = Client.expand1(expand))
    }
}

private fun BridgeState.dispatchRequest(request: BridgePayload.Request): BridgePayload.Response? =
    when (val payload = dispatch.call(RpcBuffer(payload = request)).payload) {
        is BridgePayload.Response -> payload
        else -> null
    }

private fun BridgePayload.Response?.stringValueOrNull(): String? =
    when (this) {
        is BridgePayload.Response.StringValue -> value
        else -> null
    }

internal fun ClientTokenStream.expandExprLocally(): Result<ClientTokenStream> =
    Result.Err(ExpandError().toString())

internal fun BridgeDiagnostic<ClientSpan>.toPublicDiagnostic(): Diagnostic {
    val out =
        if (spans.isEmpty()) {
            Diagnostic.new(level, message)
        } else {
            Diagnostic.spanned(SpanListMultiSpanForBridge(spans.map { it.span }), level, message)
        }
    for (child in children) {
        out.addChildDiagnostic(child.toPublicDiagnostic())
    }
    return out
}

/** Returns an independent token while retaining its data and source spans. */
public fun cloneTokenTree(tree: TokenTree): TokenTree = tree.deepClone()

private fun TokenTree.deepClone(): TokenTree =
    when (this) {
        is TokenTree.Group ->
            TokenTree.Group(
                Group(
                    GroupData(
                        delimiter = value.data.delimiter,
                        stream = ClientTokenStream(value.data.stream).let(BridgeMethods::tsClone).stream,
                        span = value.data.span.copy(),
                    ),
                ),
            )
        is TokenTree.Punct ->
            TokenTree.Punct(
                Punct(
                    PunctData(
                        ch = value.data.ch,
                        joint = value.data.joint,
                        span = value.data.span,
                    ),
                ),
            )
        is TokenTree.Ident ->
            TokenTree.Ident(
                Ident(
                    IdentData(
                        sym = value.data.sym,
                        isRaw = value.data.isRaw,
                        span = value.data.span,
                    ),
                ),
            )
        is TokenTree.Literal ->
            TokenTree.Literal(
                Literal(
                    LiteralData(
                        kind = value.data.kind,
                        symbol = value.data.symbol,
                        suffix = value.data.suffix,
                        span = value.data.span,
                    ),
                ),
            )
    }

private fun parseRustLiteral(source: String): Result<BridgeLiteral<ClientSpan>>? {
    val src = source.trim()
    if (src.isEmpty()) return Result.Err("empty literal")
    parseRawLiteral(src)?.let { rawResult ->
        return when (rawResult) {
            is Result.Ok -> Result.Ok(rawResult.value.toBridgeLiteral())
            is Result.Err -> Result.Err(rawResult.message)
        }
    }
    parseQuotedLiteral(src, prefix = "b", quote = '\'', kind = BridgeLitKind.Byte)?.let {
        return Result.Ok(it.toBridgeLiteral())
    }
    parseQuotedLiteral(src, prefix = "", quote = '\'', kind = BridgeLitKind.Char)?.let {
        return Result.Ok(it.toBridgeLiteral())
    }
    parseQuotedLiteral(src, prefix = "b", quote = '"', kind = BridgeLitKind.ByteStr)?.let {
        return Result.Ok(it.toBridgeLiteral())
    }
    parseQuotedLiteral(src, prefix = "c", quote = '"', kind = BridgeLitKind.CStr)?.let {
        return Result.Ok(it.toBridgeLiteral())
    }
    parseQuotedLiteral(src, prefix = "", quote = '"', kind = BridgeLitKind.Str)?.let {
        return Result.Ok(it.toBridgeLiteral())
    }
    parseNumericLiteral(src)?.let { parsed ->
        return when (parsed) {
            is Result.Ok -> Result.Ok(parsed.value.toBridgeLiteral())
            is Result.Err -> Result.Err(parsed.message)
        }
    }
    return null
}

private data class ParsedBridgeLiteral(
    val kind: BridgeLitKind,
    val symbol: String,
    val suffix: String?,
) {
    fun toBridgeLiteral(): BridgeLiteral<ClientSpan> =
        BridgeLiteral(
            kind = kind,
            symbol = Symbol.intern(symbol),
            suffix = suffix?.let(Symbol::intern),
            span = ClientSpan(Span.callSite()),
        )
}

private fun parseQuotedLiteral(
    src: String,
    prefix: String,
    quote: Char,
    kind: BridgeLitKind,
): ParsedBridgeLiteral? {
    if (!src.startsWith(prefix) || src.getOrNull(prefix.length) != quote) return null
    val close = findClosingQuote(src, prefix.length + 1, quote) ?: return null
    val suffix = src.substring(close + 1)
    if (!suffix.isValidLiteralSuffix()) return null
    return ParsedBridgeLiteral(kind, src.substring(prefix.length + 1, close), suffix.ifEmpty { null })
}

private fun parseRawLiteral(src: String): Result<ParsedBridgeLiteral>? {
    val candidates =
        listOf(
            "br" to { hashes: Int -> BridgeLitKind.ByteStrRaw(hashes) },
            "cr" to { hashes: Int -> BridgeLitKind.CStrRaw(hashes) },
            "r" to { hashes: Int -> BridgeLitKind.StrRaw(hashes) },
        )
    for ((prefix, kindForHashes) in candidates) {
        if (!src.startsWith(prefix)) continue
        var index = prefix.length
        while (src.getOrNull(index) == '#') index++
        if (src.getOrNull(index) != '"') continue
        val hashes = index - prefix.length
        val closeMarker = "\"" + "#".repeat(hashes)
        val close = src.indexOf(closeMarker, startIndex = index + 1)
        if (close < 0) return Result.Err("unterminated raw literal")
        val suffix = src.substring(close + closeMarker.length)
        if (!suffix.isValidLiteralSuffix()) return Result.Err("invalid literal suffix")
        val body = src.substring(index + 1, close)
        val kind = kindForHashes(hashes)
        val rawError = validateRawLiteralBody(kind, body)
        if (rawError != null) {
            return Result.Err("invalid raw literal: $rawError")
        }
        return Result.Ok(ParsedBridgeLiteral(kind, body, suffix.ifEmpty { null }))
    }
    return null
}

private fun validateRawLiteralBody(
    kind: BridgeLitKind,
    body: String,
): EscapeError? {
    var fatal: EscapeError? = null
    when (kind) {
        is BridgeLitKind.StrRaw ->
            checkRawStr(body) { _, outcome ->
                val err = outcome.errorOrNull()
                if (fatal == null && err != null && err.isFatal()) fatal = err
            }
        is BridgeLitKind.ByteStrRaw ->
            checkRawByteStr(body) { _, outcome ->
                val err = outcome.errorOrNull()
                if (fatal == null && err != null && err.isFatal()) fatal = err
            }
        is BridgeLitKind.CStrRaw ->
            checkRawCStr(body) { _, outcome ->
                val err = outcome.errorOrNull()
                if (fatal == null && err != null && err.isFatal()) fatal = err
            }
        else -> {}
    }
    return fatal
}

private fun parseNumericLiteral(src: String): Result<ParsedBridgeLiteral>? {
    if (!src.first().isAsciiDigit()) return null
    if (src.length >= 2 && src[0] == '0') {
        when (src[1]) {
            'x',
            'X',
            -> return parseRadixLiteral(src, radix = 16)
            'o',
            'O',
            -> return parseRadixLiteral(src, radix = 8)
            'b',
            'B',
            -> return parseRadixLiteral(src, radix = 2)
        }
    }
    return parseDecimalLiteral(src)
}

private fun parseRadixLiteral(
    src: String,
    radix: Int,
): Result<ParsedBridgeLiteral> {
    var index = 2
    var sawDigit = false
    while (index < src.length && (src[index] == '_' || src[index].isDigitForRadix(radix))) {
        if (src[index].isDigitForRadix(radix)) sawDigit = true
        index++
    }
    if (!sawDigit) return Result.Err("expected at least one digit in integer literal")
    val suffix = src.substring(index)
    if (!suffix.isValidLiteralSuffix()) return Result.Err("invalid literal suffix")
    return Result.Ok(ParsedBridgeLiteral(BridgeLitKind.Integer, src.substring(0, index), suffix.ifEmpty { null }))
}

private fun parseDecimalLiteral(src: String): Result<ParsedBridgeLiteral> {
    var index = consumeDecimalDigits(src, start = 0)
    var kind: BridgeLitKind = BridgeLitKind.Integer

    if (src.getOrNull(index) == '.') {
        val next = src.getOrNull(index + 1)
        when {
            next == null -> {
                index++
                kind = BridgeLitKind.Float
            }
            next.isAsciiDigit() -> {
                index = consumeDecimalDigits(src, start = index + 1)
                kind = BridgeLitKind.Float
            }
            else -> return Result.Err("expected one literal token")
        }
    }

    if (src.getOrNull(index).isExponentMarker()) {
        kind = BridgeLitKind.Float
        index = consumeExponent(src, markerIndex = index) ?: return Result.Err("expected at least one digit in exponent")
    }

    val suffix = src.substring(index)
    if (!suffix.isValidLiteralSuffix()) return Result.Err("invalid literal suffix")
    if (suffix == "f32" || suffix == "f64") kind = BridgeLitKind.Float
    return Result.Ok(ParsedBridgeLiteral(kind, src.substring(0, index), suffix.ifEmpty { null }))
}

private fun consumeDecimalDigits(
    src: String,
    start: Int,
): Int {
    var index = start
    while (index < src.length && (src[index].isAsciiDigit() || src[index] == '_')) index++
    return index
}

private fun consumeExponent(
    src: String,
    markerIndex: Int,
): Int? {
    var index = markerIndex + 1
    if (src.getOrNull(index) == '+' || src.getOrNull(index) == '-') index++
    var sawDigit = false
    while (index < src.length && (src[index].isAsciiDigit() || src[index] == '_')) {
        if (src[index].isAsciiDigit()) sawDigit = true
        index++
    }
    return if (sawDigit) index else null
}

private fun Char?.isExponentMarker(): Boolean = this == 'e' || this == 'E'

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

private fun Char.isDigitForRadix(radix: Int): Boolean =
    when (radix) {
        2 -> this == '0' || this == '1'
        8 -> this in '0'..'7'
        16 -> this.isAsciiDigit() || this in 'a'..'f' || this in 'A'..'F'
        else -> false
    }

private fun findClosingQuote(
    src: String,
    start: Int,
    quote: Char,
): Int? {
    var index = start
    while (index < src.length) {
        if (src[index] == quote && !src.isEscaped(index)) return index
        index++
    }
    return null
}

private fun String.isEscaped(index: Int): Boolean {
    var backslashes = 0
    var cursor = index - 1
    while (cursor >= 0 && this[cursor] == '\\') {
        backslashes++
        cursor--
    }
    return backslashes % 2 == 1
}

private fun String.isValidLiteralSuffix(): Boolean =
    isEmpty() || ((first().isLetter() || first() == '_') && all { it.isLetterOrDigit() || it == '_' })

private class SpanListMultiSpanForBridge(
    private val spans: List<Span>,
) : MultiSpan {
    override fun intoSpans(): SpanList = SpanList(spans)
}

private fun BridgeTokenTree<ClientTokenStream, ClientSpan>.toPublicTree(): TokenTree =
    when (this) {
        is BridgeTokenTree.Group ->
            TokenTree.Group(
                Group(
                    GroupData(
                        delimiter = value.delimiter,
                        stream = BridgeMethods.tsClone(value.stream).stream,
                        span =
                            DelimSpanData(
                                open = value.span.open.span,
                                close = value.span.close.span,
                                entire = value.span.entire.span,
                            ),
                    ),
                ),
            )
        is BridgeTokenTree.Punct ->
            TokenTree.Punct(
                Punct(
                    PunctData(
                        ch = value.ch,
                        joint = value.spacing == Spacing.JOINT,
                        span = value.span.span,
                    ),
                ),
            )
        is BridgeTokenTree.Ident ->
            TokenTree.Ident(
                Ident(
                    IdentData(
                        sym = value.sym.asString(),
                        isRaw = value.isRaw,
                        span = value.span.span,
                    ),
                ),
            )
        is BridgeTokenTree.Literal ->
            TokenTree.Literal(
                Literal(
                    LiteralData(
                        kind = value.kind.toPublicLitKind(),
                        symbol = value.symbol.asString(),
                        suffix = value.suffix?.asString(),
                        span = value.span.span,
                    ),
                ),
            )
    }

private object TrackedInputs {
    val envVars: MutableMap<String, String?> = linkedMapOf()
    val paths: MutableList<String> = mutableListOf()

    fun trackEnvVar(
        variable: String,
        value: String?,
    ) {
        envVars[variable] = value
    }

    fun trackPath(path: String) {
        paths.add(path)
    }
}
