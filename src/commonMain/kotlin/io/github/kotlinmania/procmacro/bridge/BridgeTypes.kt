// port-lint: source src/bridge/mod.rs
package io.github.kotlinmania.procmacro.bridge

import io.github.kotlinmania.procmacro.Delimiter
import io.github.kotlinmania.procmacro.Level
import io.github.kotlinmania.procmacro.Spacing
import io.github.kotlinmania.procmacro.Span
import io.github.kotlinmania.procmacro.TokenTree

/**
 * Internal interface between a proc-macro client and a compiler-front-end
 * server. Rust uses C ABI buffers and integer handles so two copies of the
 * proc-macro library can communicate across compiler boundaries. The Kotlin
 * port keeps the same logical payloads and method surface in-process.
 */
internal data class BridgeConfig(
    val input: RpcBuffer,
    val dispatch: BridgeDispatch,
    val forceShowPanics: Boolean,
    val globals: ExpnGlobals<ClientSpan> = defaultClientGlobals(),
)

internal fun interface BridgeDispatch {
    fun call(input: RpcBuffer): RpcBuffer
}

internal data class ExpnGlobals<S>(
    val defSite: S,
    val callSite: S,
    val mixedSite: S,
)

internal fun defaultClientGlobals(): ExpnGlobals<ClientSpan> =
    ExpnGlobals(
        defSite = ClientSpan(Span.defSite()),
        callSite = ClientSpan(Span.callSite()),
        mixedSite = ClientSpan(Span.mixedSite()),
    )

internal data class DelimSpan<S>(
    val open: S,
    val close: S,
    val entire: S,
)

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
