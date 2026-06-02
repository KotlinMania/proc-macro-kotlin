// port-lint: source src/lib.rs
package io.github.kotlinmania.procmacro

/**
 * A region of source code, along with macro expansion information.
 */
public class Span internal constructor(
    internal val data: SpanData,
) {
    public companion object {
        /**
         * Recover a [Span] previously serialized by [saveSpan].
         *
         * Upstream this is the inverse of `Span::save_span`: an opaque
         * `usize` ID round-trips to the original span across the
         * `bridge::client` IPC boundary. The bridge submodule is documented
         * as non-portable; phase 1 keeps an in-process registry so that
         * `quote_span` round-trips spans within a single JVM/Kotlin runtime
         * invocation. Cross-process span recovery is a phase-3 concern
         * once a Kotlin span server is selected.
         */
        public fun recoverProcMacroSpan(id: Int): Span = SpanRegistry.recover(id)

        /** A span that resolves at the macro definition site. */
        public fun defSite(): Span = Span(SpanData.DefSite)

        /**
         * The span of the invocation of the current procedural macro.
         * Identifiers created with this span will be resolved as if they
         * were written directly at the macro call location (call-site
         * hygiene) and other code at the macro call site will be able to
         * refer to them as well.
         */
        public fun callSite(): Span = Span(SpanData.CallSite)

        /**
         * A span that represents declarative-macro hygiene, and sometimes
         * resolves at the macro definition site (local variables, labels,
         * `$crate`) and sometimes at the macro call site (everything
         * else). The span location is taken from the call site.
         */
        public fun mixedSite(): Span = Span(SpanData.MixedSite)
    }

    /**
     * The [Span] for the tokens in the previous macro expansion from which
     * this was generated from, if any.
     */
    public fun parent(): Span? = null

    /**
     * The span for the origin source code that this was generated from. If
     * this [Span] wasn't generated from other macro expansions then the
     * return value is the same as `this`.
     */
    public fun source(): Span = this

    /**
     * Returns the span's byte position range in the source file.
     *
     * Phase-1 stubs (call-site, mixed-site, def-site) report an empty
     * range. Phase 3 replaces the storage with KotlinLexer-backed offsets.
     */
    public fun byteRange(): IntRange = data.byteRange()

    /** Creates an empty span pointing to directly before this span. */
    public fun start(): Span {
        val range = byteRange()
        return Span(SpanData.Synthetic(range.first..range.first - 1))
    }

    /** Creates an empty span pointing to directly after this span. */
    public fun end(): Span {
        val range = byteRange()
        return Span(SpanData.Synthetic(range.last + 1..range.last))
    }

    /**
     * The one-indexed line of the source file where the span starts.
     *
     * Phase-1 stubs report `0`. Phase 3 replaces the storage with
     * KotlinLexer-backed line numbers.
     */
    public fun line(): Int = 0

    /**
     * The one-indexed column of the source file where the span starts.
     *
     * Phase-1 stubs report `0`. Phase 3 replaces the storage with
     * KotlinLexer-backed column numbers.
     */
    public fun column(): Int = 0

    /**
     * The path to the source file in which this span occurs, for display
     * purposes. This might not correspond to a valid file system path. It
     * might be remapped (e.g. `"/src/lib.kt"`) or an artificial path
     * (e.g. `"<token stream>"`).
     *
     * Phase-1 stubs report a fixed sentinel. Phase 3 replaces the storage
     * with the KotlinLexer-backed source path.
     */
    public fun file(): String = "<token stream>"

    /**
     * The path to the source file in which this span occurs on the local
     * file system. This is the actual path on disk. It is unaffected by
     * path remapping.
     *
     * This path should not be embedded in the output of the macro; prefer
     * [file] instead.
     */
    public fun localFile(): String? = null

    /**
     * Creates a new span encompassing this and `other`.
     *
     * Returns `null` if this and `other` are from different files.
     */
    public fun join(other: Span): Span? {
        // Phase-1 stub: synthetic spans always join. Phase 3 enforces
        // the same-file invariant against KotlinLexer-backed offsets.
        if (data === other.data) return this
        return Span(SpanData.Synthetic(byteRange().first..other.byteRange().last))
    }

    /**
     * Creates a new span with the same line/column information as this but
     * that resolves symbols as though it were at `other`.
     *
     * Phase-1 has no hygiene model — returns `this`. Phase 3 may model
     * resolution context independently.
     */
    public fun resolvedAt(other: Span): Span = this

    /**
     * Creates a new span with the same name resolution behavior as this
     * but with the line/column information of `other`.
     */
    public fun locatedAt(other: Span): Span = other.resolvedAt(this)

    /**
     * Serialize this [Span] to an opaque identifier recoverable by
     * [Span.recoverProcMacroSpan]. Upstream this is the
     * `bridge::client`-side counterpart of `recover_proc_macro_span`.
     * See [Span.recoverProcMacroSpan] for the phase-1 in-process
     * registry caveat.
     */
    public fun saveSpan(): Int = SpanRegistry.save(this)

    /** Compares two spans to see if they're equal. */
    public fun eq(other: Span): Boolean = data == other.data

    /**
     * Returns the source text behind a span. This preserves the original
     * source code, including spaces and comments. It only returns a result
     * if the span corresponds to real source code.
     *
     * Phase-1 returns `null` for all spans. Phase 3 wires this to the
     * KotlinLexer-backed source map.
     */
    public fun sourceText(): String? = null

    override fun toString(): String = data.toString()
}

/**
 * Internal backing store for [Span]. Phase 1 enumerates the three sentinel
 * variants ([CallSite], [MixedSite], [DefSite]) plus a [Synthetic] variant
 * carrying a byte range; phase 3 adds a `Lexed` variant carrying real
 * source offsets and token-type information.
 */
internal sealed class SpanData {
    internal abstract fun byteRange(): IntRange

    internal data object CallSite : SpanData() {
        override fun byteRange(): IntRange = IntRange.EMPTY

        override fun toString(): String = "Span.call_site"
    }

    internal data object MixedSite : SpanData() {
        override fun byteRange(): IntRange = IntRange.EMPTY

        override fun toString(): String = "Span.mixed_site"
    }

    internal data object DefSite : SpanData() {
        override fun byteRange(): IntRange = IntRange.EMPTY

        override fun toString(): String = "Span.def_site"
    }

    internal data class Synthetic(
        val range: IntRange,
    ) : SpanData() {
        override fun byteRange(): IntRange = range

        override fun toString(): String = "Span($range)"
    }
}

/**
 * In-process backing store for [Span.saveSpan] / [Span.recoverProcMacroSpan].
 *
 * Upstream Rust uses `bridge::client` to round-trip span identifiers
 * across the proc-macro/rustc IPC boundary. The bridge submodule does
 * not port; this registry is the phase-1 stand-in that lets `quote_span`
 * preserve span identity within a single Kotlin runtime invocation.
 * Cross-process recovery is a phase-3 concern once a Kotlin span server
 * is selected.
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
 *
 * Upstream: `bridge::DelimSpan<bridge::client::Span>`.
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
