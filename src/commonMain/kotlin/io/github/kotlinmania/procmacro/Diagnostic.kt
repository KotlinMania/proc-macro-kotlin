// port-lint: source src/diagnostic.rs
package io.github.kotlinmania.procmacro

/** An enum representing a diagnostic level. */
public enum class Level {
    /** An error. */
    ERROR,

    /** A warning. */
    WARNING,

    /** A note. */
    NOTE,

    /** A help message. */
    HELP,
}

/** Interface implemented by types that can be converted into a set of [Span]s. */
public interface MultiSpan {
    /** Converts `this` into a [List] of [Span]. */
    public fun intoSpans(): List<Span>
}

/**
 * Wraps a single [Span] as a [MultiSpan]. Mirrors the upstream
 * `impl MultiSpan for Span`.
 */
public fun Span.toMultiSpan(): MultiSpan = SingleSpanMultiSpan(this)

/**
 * Wraps a list of [Span] as a [MultiSpan]. Mirrors the upstream
 * `impl MultiSpan for Vec<Span>` and `impl<'a> MultiSpan for &'a [Span]`
 * collapsed into a single implementation: Kotlin's `List<Span>` is the
 * KMP analog of both `Vec<Span>` (owned) and `&[Span]` (borrowed slice).
 * Snapshots the input at wrap time so the resulting [MultiSpan] is
 * immune to later mutation of the source list, matching the spirit of
 * the upstream `Vec<Span>::into_spans` (consuming) and `&[Span]::into_spans`
 * (copying via `to_vec`) impls.
 */
public fun List<Span>.toMultiSpan(): MultiSpan = SpanListMultiSpan(this.toList())

private class SingleSpanMultiSpan(private val span: Span) : MultiSpan {
    override fun intoSpans(): List<Span> = listOf(span)
}

private class SpanListMultiSpan(private val spans: List<Span>) : MultiSpan {
    override fun intoSpans(): List<Span> = spans
}

/**
 * A structure representing a diagnostic message and associated children
 * messages.
 */
public class Diagnostic private constructor(
    private var level: Level,
    private var message: String,
    private var spans: List<Span>,
    private val children: MutableList<Diagnostic>,
) {
    public companion object {
        /** Creates a new diagnostic with the given [level] and [message]. */
        public fun new(level: Level, message: String): Diagnostic =
            Diagnostic(level = level, message = message, spans = emptyList(), children = mutableListOf())

        /**
         * Creates a new diagnostic with the given [level] and [message]
         * pointing to the given set of [spans].
         */
        public fun spanned(spans: MultiSpan, level: Level, message: String): Diagnostic =
            Diagnostic(level = level, message = message, spans = spans.intoSpans(), children = mutableListOf())
    }

    // The upstream `diagnostic_child_methods!($spanned, $regular, $level)`
    // macro generates a pair of chainable child-adders per level:
    //   - `$spanned(spans: MultiSpan, message: String)`
    //   - `$regular(message: String)`
    // It is invoked once per level (Error, Warning, Note, Help), producing
    // the eight methods below. Kotlin has no macro system, so the eight
    // expansions are written out by hand in the same upstream order.

    /**
     * Adds a new child diagnostic message to `this` with the [Level.ERROR]
     * level, and the given [spans] and [message].
     */
    public fun spanError(spans: MultiSpan, message: String): Diagnostic {
        children.add(spanned(spans, Level.ERROR, message))
        return this
    }

    /**
     * Adds a new child diagnostic message to `this` with the [Level.ERROR]
     * level, and the given [message].
     */
    public fun error(message: String): Diagnostic {
        children.add(new(Level.ERROR, message))
        return this
    }

    /**
     * Adds a new child diagnostic message to `this` with the [Level.WARNING]
     * level, and the given [spans] and [message].
     */
    public fun spanWarning(spans: MultiSpan, message: String): Diagnostic {
        children.add(spanned(spans, Level.WARNING, message))
        return this
    }

    /**
     * Adds a new child diagnostic message to `this` with the [Level.WARNING]
     * level, and the given [message].
     */
    public fun warning(message: String): Diagnostic {
        children.add(new(Level.WARNING, message))
        return this
    }

    /**
     * Adds a new child diagnostic message to `this` with the [Level.NOTE]
     * level, and the given [spans] and [message].
     */
    public fun spanNote(spans: MultiSpan, message: String): Diagnostic {
        children.add(spanned(spans, Level.NOTE, message))
        return this
    }

    /**
     * Adds a new child diagnostic message to `this` with the [Level.NOTE]
     * level, and the given [message].
     */
    public fun note(message: String): Diagnostic {
        children.add(new(Level.NOTE, message))
        return this
    }

    /**
     * Adds a new child diagnostic message to `this` with the [Level.HELP]
     * level, and the given [spans] and [message].
     */
    public fun spanHelp(spans: MultiSpan, message: String): Diagnostic {
        children.add(spanned(spans, Level.HELP, message))
        return this
    }

    /**
     * Adds a new child diagnostic message to `this` with the [Level.HELP]
     * level, and the given [message].
     */
    public fun help(message: String): Diagnostic {
        children.add(new(Level.HELP, message))
        return this
    }

    /** Returns the diagnostic [Level] for `this`. */
    public fun level(): Level = level

    /** Sets the level in `this` to [level]. */
    public fun setLevel(level: Level) {
        this.level = level
    }

    /** Returns the message in `this`. */
    public fun message(): String = message

    /** Sets the message in `this` to [message]. */
    public fun setMessage(message: String) {
        this.message = message
    }

    /** Returns the [Span]s in `this`. */
    public fun spans(): List<Span> = spans

    /** Sets the [Span]s in `this` to [spans]. */
    public fun setSpans(spans: MultiSpan) {
        this.spans = spans.intoSpans()
    }

    /** Returns an iterator over the children diagnostics of `this`. */
    public fun children(): Children = Children(children.iterator())

    /**
     * Emit the diagnostic.
     *
     * Upstream routes through `bridge::client::Methods::emit_diagnostic`,
     * which sends the diagnostic across the proc-macro / rustc IPC bridge.
     * The bridge submodule is documented as non-portable: there is no
     * Kotlin equivalent of the rustc-side receiver. Phase 1 renders the
     * diagnostic and its children to standard output so emissions are
     * observable; consumers can redirect output if they need to capture.
     * Phase 3 may add a pluggable sink once a Kotlin diagnostics framework
     * is selected.
     */
    public fun emit() {
        println(renderDiagnostic(this))
    }
}

/** Iterator over the children diagnostics of a [Diagnostic]. */
public class Children internal constructor(
    private val inner: Iterator<Diagnostic>,
) : Iterator<Diagnostic> {
    override fun hasNext(): Boolean = inner.hasNext()
    override fun next(): Diagnostic = inner.next()
}

private fun renderDiagnostic(diag: Diagnostic, indent: Int = 0): String = buildString {
    val pad = "  ".repeat(indent)
    val label = when (diag.level()) {
        Level.ERROR -> "error"
        Level.WARNING -> "warning"
        Level.NOTE -> "note"
        Level.HELP -> "help"
    }
    append(pad).append(label).append(": ").append(diag.message())
    for (child in diag.children()) {
        append('\n').append(renderDiagnostic(child, indent + 1))
    }
}
