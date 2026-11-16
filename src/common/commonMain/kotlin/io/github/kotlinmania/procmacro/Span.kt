package io.github.kotlinmania.procmacro

import io.github.kotlinmania.procmacro.bridge.BridgeClientState
import io.github.kotlinmania.procmacro.bridge.BridgeMethods
import io.github.kotlinmania.procmacro.bridge.ClientSpan

/**
 * A region of source code, along with macro expansion information.
 */
public class Span internal constructor(
    internal val data: SpanData,
) : MultiSpan {
    public companion object {
        /**
         * Recovers a [Span] previously serialized by [saveSpan].
         */
        public fun recoverProcMacroSpan(id: Int): Span = BridgeMethods.spanRecoverProcMacroSpan(id).span

        /** A span that resolves at the macro definition site. */
        public fun defSite(): Span =
            if (BridgeClientState.isAvailable()) {
                BridgeClientState
                    .withState()
                    .globals
                    .defSite
                    .span
            } else {
                Span(SpanData.DefSite)
            }

        /**
         * The span of the invocation of the current procedural macro.
         * Identifiers created with this span will be resolved as if they
         * were written directly at the macro call location (call-site
         * hygiene) and other code at the macro call site will be able to
         * refer to them as well.
         */
        public fun callSite(): Span =
            if (BridgeClientState.isAvailable()) {
                BridgeClientState
                    .withState()
                    .globals
                    .callSite
                    .span
            } else {
                Span(SpanData.CallSite)
            }

        /**
         * A span that represents declarative-macro hygiene, and sometimes
         * resolves at the macro definition site (local variables, labels,
         * crate root markers) and sometimes at the macro call site
         * (everything else). The span location is taken from the call site.
         */
        public fun mixedSite(): Span =
            if (BridgeClientState.isAvailable()) {
                BridgeClientState
                    .withState()
                    .globals
                    .mixedSite
                    .span
            } else {
                Span(SpanData.MixedSite)
            }
    }

    /** Returns an independent copy of this [Span]. */
    public fun clone(): Span = Span(data)

    override fun intoSpans(): SpanList = SpanList(listOf(this))

    /**
     * The [Span] for the tokens in the previous macro expansion from which
     * this span was generated, if any.
     */
    public fun parent(): Span? = null

    /**
     * The span for the origin source code from which this span was generated.
     * If this [Span] was not generated from other macro expansions then the
     * return value is the same as `this`.
     */
    public fun source(): Span = this

    /**
     * Returns the byte position range of this span in the source file.
     *
     * Sentinel spans report an empty range. Synthetic spans carry explicit
     * byte bounds.
     */
    public fun byteRange(): IntRange = data.byteRange()

    /** Creates an empty span pointing directly before this span. */
    public fun start(): Span {
        val range = byteRange()
        return Span(SpanData.Synthetic(range.first..range.first - 1))
    }

    /** Creates an empty span pointing directly after this span. */
    public fun end(): Span {
        val range = byteRange()
        return Span(SpanData.Synthetic(range.last + 1..range.last))
    }

    /**
     * The one-indexed line of the source file where the span starts.
     *
     * Sentinel and synthetic spans report `0` unless backed by source offsets.
     */
    public fun line(): Int = 0

    /**
     * The one-indexed column of the source file where the span starts.
     *
     * Sentinel and synthetic spans report `0` unless backed by source offsets.
     */
    public fun column(): Int = 0

    /**
     * The path to the source file in which this span occurs, for display
     * purposes. This might not correspond to a valid file system path.
     */
    public fun file(): String = "<token stream>"

    /**
     * The path to the source file in which this span occurs on the local
     * file system. Prefer [file] when embedding paths in macro output.
     */
    public fun localFile(): String? = null

    /**
     * Creates a new span encompassing this span and [other].
     *
     * Returns `null` if this span and [other] are from different files.
     */
    public fun join(other: Span): Span? {
        if (data === other.data) return this
        return Span(SpanData.Synthetic(byteRange().first..other.byteRange().last))
    }

    /**
     * Creates a new span with the same line/column information as this span
     * that resolves symbols as though it were at [other].
     */
    public fun resolvedAt(other: Span): Span = this

    /**
     * Creates a new span with the same name resolution behavior as this span
     * and the line/column information of [other].
     */
    public fun locatedAt(other: Span): Span = other.resolvedAt(this)

    /**
     * Serializes this [Span] to an opaque identifier recoverable by
     * [Span.recoverProcMacroSpan].
     */
    public fun saveSpan(): Int = SpanRegistry.save(this)

    /** Compares two spans for equality. */
    public fun eq(other: Span): Boolean = data == other.data

    override fun equals(other: Any?): Boolean = other is Span && data == other.data

    override fun hashCode(): Int = data.hashCode()

    /**
     * Returns the source text behind this span, preserving spaces and
     * comments when backed by real source text.
     */
    public fun sourceText(): String? = BridgeMethods.spanSourceText(ClientSpan(this))

    internal fun localSourceText(): String? = null

    /** Creates a new [Diagnostic] with [Level.ERROR] and [message] at this span. */
    public fun error(message: String): Diagnostic = Diagnostic.spanned(this, Level.ERROR, message)

    /** Creates a new [Diagnostic] with [Level.WARNING] and [message] at this span. */
    public fun warning(message: String): Diagnostic = Diagnostic.spanned(this, Level.WARNING, message)

    /** Creates a new [Diagnostic] with [Level.NOTE] and [message] at this span. */
    public fun note(message: String): Diagnostic = Diagnostic.spanned(this, Level.NOTE, message)

    /** Creates a new [Diagnostic] with [Level.HELP] and [message] at this span. */
    public fun help(message: String): Diagnostic = Diagnostic.spanned(this, Level.HELP, message)

    override fun toString(): String = data.toString()
}

/**
 * Internal backing store for [Span]. Enumerates the three sentinel variants
 * ([CallSite], [MixedSite], [DefSite]) plus a [Synthetic] variant carrying a
 * byte range.
 */
internal sealed class SpanData {
    internal abstract fun byteRange(): IntRange

    internal data object CallSite : SpanData() {
        override fun byteRange(): IntRange = IntRange.EMPTY

        override fun toString(): String = "Span.callSite"
    }

    internal data object MixedSite : SpanData() {
        override fun byteRange(): IntRange = IntRange.EMPTY

        override fun toString(): String = "Span.mixedSite"
    }

    internal data object DefSite : SpanData() {
        override fun byteRange(): IntRange = IntRange.EMPTY

        override fun toString(): String = "Span.defSite"
    }

    internal data class Synthetic(
        val range: IntRange,
    ) : SpanData() {
        override fun byteRange(): IntRange = range

        override fun toString(): String = "Span($range)"
    }
}

/**
 * In-process backing store for [Span.saveSpan] and [Span.recoverProcMacroSpan].
 */
internal object SpanRegistry {
    private val saved: MutableList<Span> = mutableListOf()

    fun save(span: Span): Int {
        saved.add(span)
        return saved.size - 1
    }

    fun recover(id: Int): Span =
        saved.getOrNull(id)
            ?: throw IllegalArgumentException("SpanRegistry: no span saved with id=$id")
}

/**
 * The pair of spans pointing at the opening and closing delimiters of a
 * [Group], plus the entire span encompassing both delimiters and the
 * stream between them.
 */
internal data class DelimSpanData(
    val open: Span,
    val close: Span,
    val entire: Span,
) {
    internal companion object {
        internal fun fromSingle(span: Span): DelimSpanData = DelimSpanData(open = span, close = span, entire = span)
    }
}
