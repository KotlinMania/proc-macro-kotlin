package io.github.kotlinmania.procmacro.bridge

import io.github.kotlinmania.procmacro.Delimiter
import io.github.kotlinmania.procmacro.Level
import io.github.kotlinmania.procmacro.Spacing
import io.github.kotlinmania.procmacro.Span
import io.github.kotlinmania.procmacro.SpanData
import io.github.kotlinmania.procmacro.TokenTree

internal class Methods

/**
 * Configuration for establishing an active connection between a procedural
 * macro server and client.
 */
internal data class BridgeConfig(
    val input: RpcBuffer,
    val dispatch: BridgeDispatch,
    val forceShowPanics: Boolean,
    val globals: ExpnGlobals<ClientSpan> = defaultClientGlobals(),
    val binaryInput: Buffer = Buffer.new(),
    val binaryDispatch: Closure? = null,
)

internal fun interface BridgeDispatch {
    fun call(input: RpcBuffer): RpcBuffer
}

internal enum class ApiTags {
    InjectedEnvVar,
    TrackEnvVar,
    TrackPath,
    LiteralFromStr,
    EmitDiagnostic,
    TsDrop,
    TsClone,
    TsIsEmpty,
    TsExpandExpr,
    TsFromStr,
    TsToString,
    TsFromTokenTree,
    TsConcatTrees,
    TsConcatStreams,
    TsIntoTrees,
    SpanDebug,
    SpanParent,
    SpanSource,
    SpanByteRange,
    SpanStart,
    SpanEnd,
    SpanLine,
    SpanColumn,
    SpanFile,
    SpanLocalFile,
    SpanJoin,
    SpanSubspan,
    SpanResolvedAt,
    SpanSourceText,
    SpanSaveSpan,
    SpanRecoverProcMacroSpan,
    SymbolNormalizeAndValidateIdent,
}

internal interface Mark<Unmarked> {
    fun unmark(): Unmarked
}

internal class Marked<T, M>(
    val value: T,
) : Mark<T> {
    override fun unmark(): T = value

    override fun equals(other: Any?): Boolean = other is Marked<*, *> && value == other.value

    override fun hashCode(): Int = value?.hashCode() ?: 0

    override fun toString(): String = "Marked($value)"

    companion object {
        fun <T, M> mark(unmarked: T): Marked<T, M> = Marked(unmarked)
    }
}

/**
 * Globals provided alongside the initial inputs for a macro expansion so
 * common sentinel spans do not require an extra RPC round-trip.
 */
internal data class ExpnGlobals<S>(
    val defSite: S,
    val callSite: S,
    val mixedSite: S,
)

internal fun defaultClientGlobals(): ExpnGlobals<ClientSpan> =
    ExpnGlobals(
        defSite = ClientSpan(Span(SpanData.DefSite)),
        callSite = ClientSpan(Span(SpanData.CallSite)),
        mixedSite = ClientSpan(Span(SpanData.MixedSite)),
    )

internal data class DelimSpan<S>(
    val open: S,
    val close: S,
    val entire: S,
) {
    companion object {
        fun <S> fromSingle(span: S): DelimSpan<S> = DelimSpan(open = span, close = span, entire = span)
    }
}

internal data class BridgeGroup<TS, S>(
    val delimiter: Delimiter,
    val stream: TS,
    val span: DelimSpan<S>,
)

internal data class BridgePunct<S>(
    val ch: Char,
    val spacing: Spacing,
    val span: S,
)

internal data class BridgeIdent<S>(
    val sym: Symbol,
    val isRaw: Boolean,
    val span: S,
)

internal data class BridgeLiteral<S>(
    val kind: BridgeLitKind,
    val symbol: Symbol,
    val suffix: Symbol?,
    val span: S,
)

internal sealed class BridgeTokenTree<TS, S> {
    data class Group<TS, S>(
        val value: BridgeGroup<TS, S>,
    ) : BridgeTokenTree<TS, S>()

    data class Punct<TS, S>(
        val value: BridgePunct<S>,
    ) : BridgeTokenTree<TS, S>()

    data class Ident<TS, S>(
        val value: BridgeIdent<S>,
    ) : BridgeTokenTree<TS, S>()

    data class Literal<TS, S>(
        val value: BridgeLiteral<S>,
    ) : BridgeTokenTree<TS, S>()
}

internal sealed class BridgeLitKind {
    data object Byte : BridgeLitKind()

    data object Char : BridgeLitKind()

    data object Integer : BridgeLitKind()

    data object Float : BridgeLitKind()

    data object Str : BridgeLitKind()

    data class StrRaw(
        val numHashes: Int,
    ) : BridgeLitKind()

    data object ByteStr : BridgeLitKind()

    data class ByteStrRaw(
        val numHashes: Int,
    ) : BridgeLitKind()

    data object CStr : BridgeLitKind()

    data class CStrRaw(
        val numHashes: Int,
    ) : BridgeLitKind()

    data object ErrWithGuar : BridgeLitKind()
}

internal data class BridgeDiagnostic<S>(
    val level: Level,
    val message: String,
    val spans: List<S>,
    val children: List<BridgeDiagnostic<S>>,
)

internal fun TokenTree.toBridgeTree(): BridgeTokenTree<ClientTokenStream, ClientSpan> =
    when (this) {
        is TokenTree.Group ->
            BridgeTokenTree.Group(
                BridgeGroup(
                    delimiter = value.delimiter(),
                    stream = ClientTokenStream(value.stream()),
                    span =
                        DelimSpan(
                            open = ClientSpan(value.spanOpen()),
                            close = ClientSpan(value.spanClose()),
                            entire = ClientSpan(value.span()),
                        ),
                ),
            )
        is TokenTree.Punct ->
            BridgeTokenTree.Punct(
                BridgePunct(
                    ch = value.asChar(),
                    spacing = value.spacing(),
                    span = ClientSpan(value.span()),
                ),
            )
        is TokenTree.Ident ->
            BridgeTokenTree.Ident(
                BridgeIdent(
                    sym = Symbol.intern(value.data.sym),
                    isRaw = value.data.isRaw,
                    span = ClientSpan(value.span()),
                ),
            )
        is TokenTree.Literal ->
            BridgeTokenTree.Literal(
                BridgeLiteral(
                    kind = value.data.kind.toBridgeLitKind(),
                    symbol = Symbol.intern(value.data.symbol),
                    suffix = value.data.suffix?.let(Symbol::intern),
                    span = ClientSpan(value.span()),
                ),
            )
    }
