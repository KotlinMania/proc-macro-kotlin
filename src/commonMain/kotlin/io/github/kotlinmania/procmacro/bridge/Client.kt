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
    private val stateLocal = BridgeStateLocal()

    fun enter(
        state: BridgeState,
        block: () -> RpcBuffer,
    ): RpcBuffer {
        val stack = stateLocal.stack()
        stack.add(state)
        return try {
            block()
        } finally {
            if (stack.lastOrNull() === state) {
                stack.removeAt(stack.lastIndex)
            } else {
                stack.remove(state)
            }
        }
    }

    fun isAvailable(): Boolean = stateLocal.stack().isNotEmpty()

    fun currentOrNull(): BridgeState? = stateLocal.stack().lastOrNull()

    fun withState(): BridgeState =
        currentOrNull() ?: BridgeState(
            globals = defaultClientGlobals(),
            dispatch = BridgeDispatch { it },
        )
}

internal data class BridgeState(
    val globals: ExpnGlobals<ClientSpan>,
    val dispatch: BridgeDispatch,
    val cachedBuffer: RpcBuffer = RpcBuffer(),
)

internal object BridgeMethods {
    fun injectedEnvVar(variable: String): String? =
        BridgeClientState
            .currentOrNull()
            ?.dispatchRequest(BridgePayload.Request.InjectedEnvVar(variable))
            ?.let { it as? BridgePayload.Response.StringValue }
            ?.value

    fun trackEnvVar(
        variable: String,
        value: String?,
    ) {
        TrackedInputs.trackEnvVar(variable, value)
        BridgeClientState
            .currentOrNull()
            ?.dispatchRequest(BridgePayload.Request.TrackEnvVar(variable, value))
    }

    fun trackPath(path: String) {
        TrackedInputs.trackPath(path)
        BridgeClientState
            .currentOrNull()
            ?.dispatchRequest(BridgePayload.Request.TrackPath(path))
    }

    fun literalFromStr(source: String): Result<BridgeLiteral<ClientSpan>> =
        parseRustLiteral(source)
            ?: when (val parsed = TokenStream.fromString(source)) {
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
        diagnostic.toPublicDiagnostic().emit()
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
        val length = if (range.isEmpty()) 0 else range.last - range.first + 1
        if (start < 0 || end > length || start > end) return null
        return ClientSpan(Span(SpanData.Synthetic(range.first + start until range.first + end)))
    }

    fun spanResolvedAt(
        span: ClientSpan,
        at: ClientSpan,
    ): ClientSpan = ClientSpan(span.span.resolvedAt(at.span))

    fun spanSourceText(span: ClientSpan): String? =
        BridgeClientState
            .currentOrNull()
            ?.dispatchRequest(BridgePayload.Request.SpanSourceText(span))
            ?.let { it as? BridgePayload.Response.StringValue }
            ?.value
            ?: span.span.sourceText()

    fun spanSaveSpan(span: ClientSpan): Int = span.span.saveSpan()

    fun spanRecoverProcMacroSpan(id: Int): ClientSpan = ClientSpan(Span.recoverProcMacroSpan(id))

    fun symbolNormalizeAndValidateIdent(string: String): Result<Symbol> =
        Symbol.normalizeAndValidateIdent(string)
}

private fun BridgeState.dispatchRequest(request: BridgePayload.Request): BridgePayload.Response? =
    dispatch.call(RpcBuffer(payload = request)).payload as? BridgePayload.Response

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
    parseRawLiteral(src)?.let { return Result.Ok(it.toBridgeLiteral()) }
    parseQuotedLiteral(src, prefix = "b", quote = '\'', kind = BridgeLitKind.Byte)?.let { return Result.Ok(it.toBridgeLiteral()) }
    parseQuotedLiteral(src, prefix = "", quote = '\'', kind = BridgeLitKind.Char)?.let { return Result.Ok(it.toBridgeLiteral()) }
    parseQuotedLiteral(src, prefix = "b", quote = '"', kind = BridgeLitKind.ByteStr)?.let { return Result.Ok(it.toBridgeLiteral()) }
    parseQuotedLiteral(src, prefix = "c", quote = '"', kind = BridgeLitKind.CStr)?.let { return Result.Ok(it.toBridgeLiteral()) }
    parseQuotedLiteral(src, prefix = "", quote = '"', kind = BridgeLitKind.Str)?.let { return Result.Ok(it.toBridgeLiteral()) }
    parseNumericLiteral(src)?.let { return Result.Ok(it.toBridgeLiteral()) }
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

private fun parseRawLiteral(src: String): ParsedBridgeLiteral? {
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
        if (close < 0) return null
        val suffix = src.substring(close + closeMarker.length)
        if (!suffix.isValidLiteralSuffix()) return null
        return ParsedBridgeLiteral(kindForHashes(hashes), src.substring(index + 1, close), suffix.ifEmpty { null })
    }
    return null
}

private fun parseNumericLiteral(src: String): ParsedBridgeLiteral? {
    val match = rustNumericLiteral.matchEntire(src) ?: return null
    val number = match.groupValues[1]
    val suffix = match.groupValues.getOrNull(2)?.ifEmpty { null }
    val kind = if (number.any { it == '.' || it == 'e' || it == 'E' }) BridgeLitKind.Float else BridgeLitKind.Integer
    return ParsedBridgeLiteral(kind, number, suffix)
}

private val rustNumericLiteral =
    Regex(
        """^((?:0[xX][0-9A-Fa-f_]+|0[oO][0-7_]+|0[bB][01_]+|[0-9][0-9_]*(?:\.[0-9_]+)?(?:[eE][+-]?[0-9_]+)?))([A-Za-z_][A-Za-z0-9_]*)?$""",
    )

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
    isEmpty() || (first().isLetter() || first() == '_') && all { it.isLetterOrDigit() || it == '_' }

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
