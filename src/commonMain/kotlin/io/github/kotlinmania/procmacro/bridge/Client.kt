// port-lint: source src/bridge/client.rs
package io.github.kotlinmania.procmacro.bridge

import io.github.kotlinmania.procmacro.Diagnostic
import io.github.kotlinmania.procmacro.ExpandError
import io.github.kotlinmania.procmacro.Group
import io.github.kotlinmania.procmacro.GroupData
import io.github.kotlinmania.procmacro.Ident
import io.github.kotlinmania.procmacro.IdentData
import io.github.kotlinmania.procmacro.Literal
import io.github.kotlinmania.procmacro.LiteralData
import io.github.kotlinmania.procmacro.Punct
import io.github.kotlinmania.procmacro.PunctData
import io.github.kotlinmania.procmacro.Spacing
import io.github.kotlinmania.procmacro.Span
import io.github.kotlinmania.procmacro.SpanData
import io.github.kotlinmania.procmacro.SpanList
import io.github.kotlinmania.procmacro.TokenStream
import io.github.kotlinmania.procmacro.TokenStreamData
import io.github.kotlinmania.procmacro.TokenStreamExpandOutcome
import io.github.kotlinmania.procmacro.TokenStreamParseOutcome
import io.github.kotlinmania.procmacro.TokenTree

internal data class ClientTokenStream(
    val stream: TokenStream,
)

internal data class ClientSpan(
    val span: Span,
)

internal object BridgeClientState {
    private var current: BridgeState? = null

    fun enter(
        state: BridgeState,
        block: () -> RpcBuffer,
    ): RpcBuffer {
        val previous = current
        current = state
        return try {
            block()
        } finally {
            current = previous
        }
    }

    fun isAvailable(): Boolean = current != null

    fun withState(): BridgeState =
        current ?: BridgeState(
            globals =
                ExpnGlobals(
                    defSite = ClientSpan(Span.defSite()),
                    callSite = ClientSpan(Span.callSite()),
                    mixedSite = ClientSpan(Span.mixedSite()),
                ),
            dispatch = BridgeDispatch { it },
        )
}

internal data class BridgeState(
    val globals: ExpnGlobals<ClientSpan>,
    val dispatch: BridgeDispatch,
    val cachedBuffer: RpcBuffer = RpcBuffer(),
)

internal object BridgeMethods {
    fun injectedEnvVar(variable: String): String? = null

    fun trackEnvVar(
        variable: String,
        value: String?,
    ) {
        TrackedInputs.trackEnvVar(variable, value)
    }

    fun trackPath(path: String) {
        TrackedInputs.trackPath(path)
    }

    fun literalFromStr(source: String): Result<BridgeLiteral<ClientSpan>> =
        when (val parsed = TokenStream.fromString(source)) {
            is TokenStreamParseOutcome.Ok -> {
                val literal =
                    parsed
                        .value
                        .data
                        .trees
                        .singleOrNull() as? TokenTree.Literal
                if (literal == null) {
                    Result.Err("expected one literal token")
                } else {
                    Result.Ok((literal.toBridgeTree() as BridgeTokenTree.Literal<ClientTokenStream, ClientSpan>).value)
                }
            }
            is TokenStreamParseOutcome.Err -> Result.Err(parsed.error.toString())
        }

    fun emitDiagnostic(diagnostic: BridgeDiagnostic<ClientSpan>) {
        Diagnostic
            .spanned(
                SpanListMultiSpanForBridge(diagnostic.spans.map { it.span }),
                diagnostic.level,
                diagnostic.message,
            ).emit()
    }

    fun tsDrop(stream: ClientTokenStream) {
        stream.stream.data.trees = emptyList()
    }

    fun tsClone(stream: ClientTokenStream): ClientTokenStream =
        ClientTokenStream(
            TokenStream(
                TokenStreamData(
                    stream.stream.data.trees
                        .toList(),
                ),
            ),
        )

    fun tsIsEmpty(stream: ClientTokenStream): Boolean = stream.stream.isEmpty()

    fun tsExpandExpr(stream: ClientTokenStream): Result<ClientTokenStream> =
        when (val expanded = stream.stream.expandExpr()) {
            is TokenStreamExpandOutcome.Ok -> Result.Ok(ClientTokenStream(expanded.value))
            is TokenStreamExpandOutcome.Err -> Result.Err(ExpandError().toString())
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
        val range = span.span.byteRange()
        if (start < range.first || end > range.last + 1 || start > end) return null
        return ClientSpan(Span(SpanData.Synthetic(start until end)))
    }

    fun spanResolvedAt(
        span: ClientSpan,
        at: ClientSpan,
    ): ClientSpan = ClientSpan(span.span.resolvedAt(at.span))

    fun spanSourceText(span: ClientSpan): String? = span.span.sourceText()

    fun spanSaveSpan(span: ClientSpan): Int = span.span.saveSpan()

    fun spanRecoverProcMacroSpan(id: Int): ClientSpan = ClientSpan(Span.recoverProcMacroSpan(id))

    fun symbolNormalizeAndValidateIdent(string: String): Result<Symbol> =
        Symbol.normalizeAndValidateIdent(string)
}

private class SpanListMultiSpanForBridge(
    private val spans: List<Span>,
) : io.github.kotlinmania.procmacro.MultiSpan {
    override fun intoSpans(): SpanList = SpanList(spans)
}

private fun BridgeTokenTree<ClientTokenStream, ClientSpan>.toPublicTree(): TokenTree =
    when (this) {
        is BridgeTokenTree.Group ->
            TokenTree.Group(
                Group(
                    GroupData(
                        delimiter = value.delimiter,
                        stream = value.stream.stream,
                        span =
                            io.github.kotlinmania.procmacro.DelimSpanData(
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
