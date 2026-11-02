// port-lint: source src/bridge/rpc.rs
package io.github.kotlinmania.procmacro.bridge

import io.github.kotlinmania.procmacro.Delimiter
import io.github.kotlinmania.procmacro.Level
import io.github.kotlinmania.procmacro.Spacing

internal interface Encode<S> {
    fun encode(
        w: Buffer,
        s: S,
    )
}

internal interface Decode<S, T> {
    fun decode(
        r: BufferReader,
        s: S,
    ): T
}

internal class RpcBuffer(
    private val bytes: MutableList<Byte> = mutableListOf(),
    internal val payload: BridgePayload? = null,
) {
    val size: Int get() = bytes.size

    fun isEmpty(): Boolean = bytes.isEmpty()

    fun clear() {
        bytes.clear()
    }

    fun append(byte: Byte) {
        bytes.add(byte)
    }

    fun appendAll(input: ByteArray) {
        for (byte in input) bytes.add(byte)
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()

    fun toBuffer(): Buffer = Buffer.from(toByteArray())

    fun copy(): RpcBuffer = RpcBuffer(bytes.toMutableList(), payload)

    companion object {
        fun fromBuffer(buffer: Buffer): RpcBuffer =
            RpcBuffer(bytes = buffer.toByteArray().toMutableList())
    }
}

/**
 * Binary little-endian RPC codec matching the client-server wire format.
 */
internal object RpcCodec {
    fun encodeUnit() {
        // Zero-byte representation on the wire.
    }

    fun decodeUnit() {
        // Zero-byte representation on the wire.
    }

    fun encodeUByte(
        w: Buffer,
        value: UByte,
    ) {
        w.push(value)
    }

    fun decodeUByte(r: BufferReader): UByte = r.readUByte()

    fun encodeUInt(
        w: Buffer,
        value: UInt,
    ) {
        val bytes =
            byteArrayOf(
                (value and 0xffu).toByte(),
                ((value shr 8) and 0xffu).toByte(),
                ((value shr 16) and 0xffu).toByte(),
                ((value shr 24) and 0xffu).toByte(),
            )
        w.extendFromArray(bytes)
    }

    fun decodeUInt(r: BufferReader): UInt {
        val b = r.readBytes(4)
        val b0 = (b[0].toInt() and 0xff).toUInt()
        val b1 = (b[1].toInt() and 0xff).toUInt()
        val b2 = (b[2].toInt() and 0xff).toUInt()
        val b3 = (b[3].toInt() and 0xff).toUInt()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    fun encodeInt(
        w: Buffer,
        value: Int,
    ) {
        encodeUInt(w, value.toUInt())
    }

    fun decodeInt(r: BufferReader): Int = decodeUInt(r).toInt()

    fun encodeIntRange(
        w: Buffer,
        range: IntRange,
    ) {
        if (range.isEmpty()) {
            encodeBoolean(w, true)
        } else {
            encodeBoolean(w, false)
            encodeInt(w, range.first)
            encodeInt(w, range.last)
        }
    }

    fun decodeIntRange(r: BufferReader): IntRange =
        if (decodeBoolean(r)) {
            IntRange.EMPTY
        } else {
            val first = decodeInt(r)
            val last = decodeInt(r)
            first..last
        }

    fun encodeBoolean(
        w: Buffer,
        value: Boolean,
    ) {
        encodeUByte(w, if (value) 1u else 0u)
    }

    fun decodeBoolean(r: BufferReader): Boolean =
        when (val tag = decodeUByte(r).toInt()) {
            0 -> false
            1 -> true
            else -> error("invalid boolean RPC tag: $tag")
        }

    fun encodeString(
        w: Buffer,
        value: String,
    ) {
        val utf8 = value.encodeToByteArray()
        encodeInt(w, utf8.size)
        w.writeAll(utf8)
    }

    fun decodeString(r: BufferReader): String {
        val len = decodeInt(r)
        check(len >= 0) { "negative RPC string length: $len" }
        return r.readBytes(len).decodeToString()
    }

    fun <T> encodeNullable(
        w: Buffer,
        value: T?,
        encodeItem: (Buffer, T) -> Unit,
    ) {
        if (value == null) {
            encodeUByte(w, 0u)
        } else {
            encodeUByte(w, 1u)
            encodeItem(w, value)
        }
    }

    fun <T> decodeNullable(
        r: BufferReader,
        decodeItem: (BufferReader) -> T,
    ): T? =
        when (val tag = decodeUByte(r).toInt()) {
            0 -> null
            1 -> decodeItem(r)
            else -> error("invalid option RPC tag: $tag")
        }

    fun <T> encodeList(
        w: Buffer,
        items: List<T>,
        encodeItem: (Buffer, T) -> Unit,
    ) {
        encodeInt(w, items.size)
        for (item in items) {
            encodeItem(w, item)
        }
    }

    fun <T> decodeList(
        r: BufferReader,
        decodeItem: (BufferReader) -> T,
    ): List<T> {
        val len = decodeInt(r)
        check(len >= 0) { "negative RPC list length: $len" }
        val result = ArrayList<T>(len)
        repeat(len) {
            result.add(decodeItem(r))
        }
        return result
    }

    fun <T> encodeResult(
        w: Buffer,
        result: Result<T>,
        encodeOk: (Buffer, T) -> Unit,
    ) {
        when (result) {
            is Result.Ok -> {
                encodeUByte(w, 0u)
                encodeOk(w, result.value)
            }
            is Result.Err -> {
                encodeUByte(w, 1u)
                encodeString(w, result.message)
            }
        }
    }

    fun <T> decodeResult(
        r: BufferReader,
        decodeOk: (BufferReader) -> T,
    ): Result<T> =
        when (val tag = decodeUByte(r).toInt()) {
            0 -> Result.Ok(decodeOk(r))
            1 -> Result.Err(decodeString(r))
            else -> error("invalid result RPC tag: $tag")
        }

    fun <T> encodePanicOutcome(
        w: Buffer,
        outcome: PanicOutcome<T>,
        encodeOk: (Buffer, T) -> Unit,
    ) {
        when (outcome) {
            is PanicOutcome.Ok -> {
                encodeUByte(w, 0u)
                encodeOk(w, outcome.value)
            }
            is PanicOutcome.Panicked -> {
                encodeUByte(w, 1u)
                outcome.panic.encode(w)
            }
        }
    }

    fun <T> decodePanicOutcome(
        r: BufferReader,
        decodeOk: (BufferReader) -> T,
    ): PanicOutcome<T> =
        when (val tag = decodeUByte(r).toInt()) {
            0 -> PanicOutcome.Ok(decodeOk(r))
            1 -> PanicOutcome.Panicked(PanicMessage.decode(r))
            else -> error("invalid panic-outcome RPC tag: $tag")
        }

    fun encodeApiTag(
        w: Buffer,
        tag: ApiTags,
    ) {
        encodeUByte(w, tag.ordinal.toUByte())
    }

    fun decodeApiTag(r: BufferReader): ApiTags {
        val ordinal = decodeUByte(r).toInt()
        return ApiTags.entries.getOrElse(ordinal) {
            error("unknown ApiTags ordinal: $ordinal")
        }
    }

    fun encodeDelimiter(
        w: Buffer,
        delimiter: Delimiter,
    ) {
        encodeUByte(w, delimiter.ordinal.toUByte())
    }

    fun decodeDelimiter(r: BufferReader): Delimiter {
        val ordinal = decodeUByte(r).toInt()
        return Delimiter.entries.getOrElse(ordinal) {
            error("unknown Delimiter ordinal: $ordinal")
        }
    }

    fun encodeSpacing(
        w: Buffer,
        spacing: Spacing,
    ) {
        encodeUByte(w, spacing.ordinal.toUByte())
    }

    fun decodeSpacing(r: BufferReader): Spacing {
        val ordinal = decodeUByte(r).toInt()
        return Spacing.entries.getOrElse(ordinal) {
            error("unknown Spacing ordinal: $ordinal")
        }
    }

    fun encodeLevel(
        w: Buffer,
        level: Level,
    ) {
        encodeUByte(w, level.ordinal.toUByte())
    }

    fun decodeLevel(r: BufferReader): Level {
        val ordinal = decodeUByte(r).toInt()
        return Level.entries.getOrElse(ordinal) {
            error("unknown Level ordinal: $ordinal")
        }
    }

    fun encodeLitKind(
        w: Buffer,
        kind: BridgeLitKind,
    ) {
        when (kind) {
            BridgeLitKind.Byte -> encodeUByte(w, 0u)
            BridgeLitKind.Char -> encodeUByte(w, 1u)
            BridgeLitKind.Integer -> encodeUByte(w, 2u)
            BridgeLitKind.Float -> encodeUByte(w, 3u)
            BridgeLitKind.Str -> encodeUByte(w, 4u)
            is BridgeLitKind.StrRaw -> {
                encodeUByte(w, 5u)
                encodeInt(w, kind.numHashes)
            }
            BridgeLitKind.ByteStr -> encodeUByte(w, 6u)
            is BridgeLitKind.ByteStrRaw -> {
                encodeUByte(w, 7u)
                encodeInt(w, kind.numHashes)
            }
            BridgeLitKind.CStr -> encodeUByte(w, 8u)
            is BridgeLitKind.CStrRaw -> {
                encodeUByte(w, 9u)
                encodeInt(w, kind.numHashes)
            }
            BridgeLitKind.ErrWithGuar -> encodeUByte(w, 10u)
        }
    }

    fun decodeLitKind(r: BufferReader): BridgeLitKind =
        when (val tag = decodeUByte(r).toInt()) {
            0 -> BridgeLitKind.Byte
            1 -> BridgeLitKind.Char
            2 -> BridgeLitKind.Integer
            3 -> BridgeLitKind.Float
            4 -> BridgeLitKind.Str
            5 -> BridgeLitKind.StrRaw(decodeInt(r))
            6 -> BridgeLitKind.ByteStr
            7 -> BridgeLitKind.ByteStrRaw(decodeInt(r))
            8 -> BridgeLitKind.CStr
            9 -> BridgeLitKind.CStrRaw(decodeInt(r))
            10 -> BridgeLitKind.ErrWithGuar
            else -> error("unknown BridgeLitKind RPC tag: $tag")
        }

    fun <S> encodeExpnGlobals(
        w: Buffer,
        globals: ExpnGlobals<S>,
        encodeSpan: (Buffer, S) -> Unit,
    ) {
        encodeSpan(w, globals.defSite)
        encodeSpan(w, globals.callSite)
        encodeSpan(w, globals.mixedSite)
    }

    fun <S> decodeExpnGlobals(
        r: BufferReader,
        decodeSpan: (BufferReader) -> S,
    ): ExpnGlobals<S> =
        ExpnGlobals(
            defSite = decodeSpan(r),
            callSite = decodeSpan(r),
            mixedSite = decodeSpan(r),
        )

    fun <TS, S> encodeTokenTree(
        w: Buffer,
        tree: BridgeTokenTree<TS, S>,
        encodeStream: (Buffer, TS) -> Unit,
        encodeSpan: (Buffer, S) -> Unit,
    ) {
        when (tree) {
            is BridgeTokenTree.Group -> {
                encodeUByte(w, 0u)
                encodeDelimiter(w, tree.value.delimiter)
                encodeStream(w, tree.value.stream)
                encodeSpan(w, tree.value.span.open)
                encodeSpan(w, tree.value.span.close)
                encodeSpan(w, tree.value.span.entire)
            }
            is BridgeTokenTree.Punct -> {
                encodeUByte(w, 1u)
                encodeInt(w, tree.value.ch.code)
                encodeSpacing(w, tree.value.spacing)
                encodeSpan(w, tree.value.span)
            }
            is BridgeTokenTree.Ident -> {
                encodeUByte(w, 2u)
                tree.value.sym.encode(w)
                encodeBoolean(w, tree.value.isRaw)
                encodeSpan(w, tree.value.span)
            }
            is BridgeTokenTree.Literal -> {
                encodeUByte(w, 3u)
                encodeLitKind(w, tree.value.kind)
                tree.value.symbol.encode(w)
                encodeNullable(w, tree.value.suffix) { buf, sym -> sym.encode(buf) }
                encodeSpan(w, tree.value.span)
            }
        }
    }

    fun <TS, S> decodeTokenTree(
        r: BufferReader,
        decodeStream: (BufferReader) -> TS,
        decodeSpan: (BufferReader) -> S,
    ): BridgeTokenTree<TS, S> =
        when (val tag = decodeUByte(r).toInt()) {
            0 ->
                BridgeTokenTree.Group(
                    BridgeGroup(
                        delimiter = decodeDelimiter(r),
                        stream = decodeStream(r),
                        span =
                            DelimSpan(
                                open = decodeSpan(r),
                                close = decodeSpan(r),
                                entire = decodeSpan(r),
                            ),
                    ),
                )
            1 ->
                BridgeTokenTree.Punct(
                    BridgePunct(
                        ch = decodeInt(r).toChar(),
                        spacing = decodeSpacing(r),
                        span = decodeSpan(r),
                    ),
                )
            2 ->
                BridgeTokenTree.Ident(
                    BridgeIdent(
                        sym = Symbol.decode(r),
                        isRaw = decodeBoolean(r),
                        span = decodeSpan(r),
                    ),
                )
            3 ->
                BridgeTokenTree.Literal(
                    BridgeLiteral(
                        kind = decodeLitKind(r),
                        symbol = Symbol.decode(r),
                        suffix = decodeNullable(r) { Symbol.decode(it) },
                        span = decodeSpan(r),
                    ),
                )
            else -> error("unknown BridgeTokenTree RPC tag: $tag")
        }

    fun <S> encodeDiagnostic(
        w: Buffer,
        diagnostic: BridgeDiagnostic<S>,
        encodeSpan: (Buffer, S) -> Unit,
    ) {
        encodeLevel(w, diagnostic.level)
        encodeString(w, diagnostic.message)
        encodeList(w, diagnostic.spans, encodeSpan)
        encodeList(w, diagnostic.children) { buf, child -> encodeDiagnostic(buf, child, encodeSpan) }
    }

    fun <S> decodeDiagnostic(
        r: BufferReader,
        decodeSpan: (BufferReader) -> S,
    ): BridgeDiagnostic<S> =
        BridgeDiagnostic(
            level = decodeLevel(r),
            message = decodeString(r),
            spans = decodeList(r, decodeSpan),
            children = decodeList(r) { decodeDiagnostic(it, decodeSpan) },
        )
}

internal class UnknownPanicMessage

internal sealed class PanicOutcome<out T> {
    data class Ok<T>(
        val value: T,
    ) : PanicOutcome<T>()

    data class Panicked(
        val panic: PanicMessage,
    ) : PanicOutcome<Nothing>()

    fun <R> map(transform: (T) -> R): PanicOutcome<R> =
        when (this) {
            is Ok -> Ok(transform(value))
            is Panicked -> this
        }

    fun unwrapOrThrow(): T =
        when (this) {
            is Ok -> value
            is Panicked -> throw IllegalStateException(panic.asStr() ?: "procedural macro panicked")
        }
}

/** Simplified panic payloads retaining static and owned string messages. */
internal sealed class PanicMessage {
    data class StaticStr(
        val value: kotlin.String,
    ) : PanicMessage()

    data class String(
        val value: kotlin.String,
    ) : PanicMessage()

    data object Unknown : PanicMessage()

    fun asStr(): kotlin.String? =
        when (this) {
            is StaticStr -> value
            is String -> value
            Unknown -> null
        }

    fun intoString(): kotlin.String? =
        when (this) {
            is StaticStr -> value
            is String -> value
            Unknown -> null
        }

    fun encode(w: Buffer) {
        RpcCodec.encodeNullable(w, asStr()) { buf, s -> RpcCodec.encodeString(buf, s) }
    }

    fun toAnyPayload(): Any =
        when (this) {
            is StaticStr -> value
            is String -> value
            Unknown -> UnknownPanicMessage()
        }

    companion object {
        fun from(payload: Any?): PanicMessage =
            when (payload) {
                is kotlin.String -> String(payload)
                is Throwable -> payload.message?.let(::String) ?: Unknown
                else -> Unknown
            }

        fun decode(r: BufferReader): PanicMessage =
            when (val text = RpcCodec.decodeNullable(r) { RpcCodec.decodeString(it) }) {
                null -> Unknown
                else -> String(text)
            }
    }
}

internal sealed class BridgePayload {
    sealed class Request : BridgePayload() {
        data class InjectedEnvVar(
            val variable: String,
        ) : Request()

        data class TrackEnvVar(
            val variable: String,
            val value: String?,
        ) : Request()

        data class TrackPath(
            val path: String,
        ) : Request()

        data class SpanSourceText(
            val span: ClientSpan,
        ) : Request()

        data class ExpandExpr(
            val stream: ClientTokenStream,
        ) : Request()
    }

    sealed class Response : BridgePayload() {
        data class StringValue(
            val value: String?,
        ) : Response()

        data class TokenStreamResult(
            val result: Result<ClientTokenStream>,
        ) : Response()

        data object UnitValue : Response()
    }
}

internal sealed class Result<out T> {
    data class Ok<T>(
        val value: T,
    ) : Result<T>()

    data class Err(
        val message: String,
    ) : Result<Nothing>()

    fun <R> map(transform: (T) -> R): Result<R> =
        when (this) {
            is Ok -> Ok(transform(value))
            is Err -> this
        }

    fun getOrThrow(): T =
        when (this) {
            is Ok -> value
            is Err -> throw IllegalStateException(message)
        }
}
