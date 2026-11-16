// port-lint: source src/diagnostic.rs
package io.github.kotlinmania.procmacro

import io.github.kotlinmania.procmacro.bridge.BridgeClientState
import io.github.kotlinmania.procmacro.bridge.BridgeDiagnostic
import io.github.kotlinmania.procmacro.bridge.BridgeMethods
import io.github.kotlinmania.procmacro.bridge.ClientSpan

/** An enum representing a diagnostic severity level. */
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

/** Interface implemented by types convertible into a sequence of [Span] values. */
public interface MultiSpan {
    /** Converts this value into a concrete [SpanList]. */
    public fun intoSpans(): SpanList
}

public class SpanList internal constructor(
    private val spans: List<Span>,
) {
    public val size: Int get() = spans.size

    public fun isEmpty(): Boolean = spans.isEmpty()

    public fun get(index: Int): Span = spans[index]

    internal fun asList(): List<Span> = spans
}

/** Wraps a single [Span] as a [MultiSpan]. */
public fun Span.toMultiSpan(): MultiSpan = SingleSpanMultiSpan(this)

/**
 * Wraps a list of [Span] values as an immutable [MultiSpan] snapshot.
 */
internal fun List<Span>.toMultiSpan(): MultiSpan = SpanListMultiSpan(this.toList())

public fun SpanList.toMultiSpan(): MultiSpan = SpanListMultiSpan(this.asList())

private class SingleSpanMultiSpan(
    private val span: Span,
) : MultiSpan {
    override fun intoSpans(): SpanList = SpanList(listOf(span))
}

private class SpanListMultiSpan(
    private val spans: List<Span>,
) : MultiSpan {
    override fun intoSpans(): SpanList = SpanList(spans)
}

/**
 * A diagnostic message together with its primary spans and child diagnostics.
 */
public class Diagnostic private constructor(
    private var level: Level,
    private var message: String,
    private var spans: SpanList,
    private val children: MutableList<Diagnostic>,
) {
    public companion object {
        /** Creates a new diagnostic with the given [level] and [message]. */
        public fun new(
            level: Level,
            message: String,
        ): Diagnostic =
            Diagnostic(level = level, message = message, spans = SpanList(emptyList()), children = mutableListOf())

        /**
         * Creates a new diagnostic with the given [level] and [message]
         * pointing to the given set of [spans].
         */
        public fun spanned(
            spans: MultiSpan,
            level: Level,
            message: String,
        ): Diagnostic =
            Diagnostic(level = level, message = message, spans = spans.intoSpans(), children = mutableListOf())
    }

    /** Returns a deep copy of this diagnostic and its child diagnostics. */
    public fun clone(): Diagnostic =
        Diagnostic(
            level = level,
            message = message,
            spans = SpanList(spans.asList().toList()),
            children = children.map { it.clone() }.toMutableList(),
        )

    /**
     * Adds a child diagnostic with [Level.ERROR] at [spans] and [message].
     */
    public fun spanError(
        spans: MultiSpan,
        message: String,
    ): Diagnostic {
        children.add(spanned(spans, Level.ERROR, message))
        return this
    }

    /**
     * Adds a child diagnostic with [Level.ERROR] and [message].
     */
    public fun error(message: String): Diagnostic {
        children.add(new(Level.ERROR, message))
        return this
    }

    /**
     * Adds a child diagnostic with [Level.WARNING] at [spans] and [message].
     */
    public fun spanWarning(
        spans: MultiSpan,
        message: String,
    ): Diagnostic {
        children.add(spanned(spans, Level.WARNING, message))
        return this
    }

    /**
     * Adds a child diagnostic with [Level.WARNING] and [message].
     */
    public fun warning(message: String): Diagnostic {
        children.add(new(Level.WARNING, message))
        return this
    }

    /**
     * Adds a child diagnostic with [Level.NOTE] at [spans] and [message].
     */
    public fun spanNote(
        spans: MultiSpan,
        message: String,
    ): Diagnostic {
        children.add(spanned(spans, Level.NOTE, message))
        return this
    }

    /**
     * Adds a child diagnostic with [Level.NOTE] and [message].
     */
    public fun note(message: String): Diagnostic {
        children.add(new(Level.NOTE, message))
        return this
    }

    /**
     * Adds a child diagnostic with [Level.HELP] at [spans] and [message].
     */
    public fun spanHelp(
        spans: MultiSpan,
        message: String,
    ): Diagnostic {
        children.add(spanned(spans, Level.HELP, message))
        return this
    }

    /**
     * Adds a child diagnostic with [Level.HELP] and [message].
     */
    public fun help(message: String): Diagnostic {
        children.add(new(Level.HELP, message))
        return this
    }

    /** Returns the diagnostic [Level]. */
    public fun level(): Level = level

    /** Updates the diagnostic [Level]. */
    public fun setLevel(level: Level) {
        this.level = level
    }

    /** Returns the diagnostic message text. */
    public fun message(): String = message

    /** Updates the diagnostic message text. */
    public fun setMessage(message: String) {
        this.message = message
    }

    /** Returns the [SpanList] attached to this diagnostic. */
    public fun spans(): SpanList = spans

    /** Replaces the spans attached to this diagnostic via [MultiSpan]. */
    public fun setSpans(spans: MultiSpan) {
        this.spans = spans.intoSpans()
    }

    /** Returns an iterator over the child diagnostics. */
    public fun children(): Children = Children(children.iterator())

    internal fun addChildDiagnostic(child: Diagnostic): Diagnostic {
        children.add(child)
        return this
    }

    internal fun toBridgeDiagnostic(): BridgeDiagnostic<ClientSpan> =
        BridgeDiagnostic(
            level = level,
            message = message,
            spans = spans.asList().map(::ClientSpan),
            children = children.map { it.toBridgeDiagnostic() },
        )

    internal fun emitLocally() {
        println(renderDiagnostic(this))
    }

    /** Emits this diagnostic through the active bridge or standard output. */
    public fun emit() {
        if (BridgeClientState.isAvailable()) {
            BridgeMethods.emitDiagnostic(toBridgeDiagnostic())
        } else {
            emitLocally()
        }
    }
}

/** Iterator over the child diagnostics of a [Diagnostic]. */
public class Children internal constructor(
    private val inner: Iterator<Diagnostic>,
) : Iterator<Diagnostic> {
    override fun hasNext(): Boolean = inner.hasNext()

    override fun next(): Diagnostic {
        if (!hasNext()) {
            throw NoSuchElementException()
        }
        return inner.next()
    }
}

private fun renderDiagnostic(
    diag: Diagnostic,
    indent: Int = 0,
): String =
    buildString {
        val pad = "  ".repeat(indent)
        val label =
            when (diag.level()) {
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
