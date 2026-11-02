package io.github.kotlinmania.procmacro

/**
 * A [Punct] is a single punctuation character such as `+`, `-`, or `#`.
 *
 * Multi-character operators like `+=` are represented as two instances of
 * [Punct] with different [Spacing] values.
 */
public class Punct internal constructor(
    internal val data: PunctData,
) {
    public companion object {
        private val LEGAL_CHARS: Set<Char> =
            setOf(
                '=',
                '<',
                '>',
                '!',
                '~',
                '+',
                '-',
                '*',
                '/',
                '%',
                '^',
                '&',
                '|',
                '@',
                '.',
                ',',
                ';',
                ':',
                '#',
                '$',
                '?',
                '\'',
            )

        /**
         * Creates a new [Punct] from the given character and spacing.
         *
         * Throws [IllegalArgumentException] if [ch] is not a valid
         * punctuation character. The returned [Punct] defaults to
         * [Span.callSite], configurable via [setSpan].
         */
        public fun new(
            ch: Char,
            spacing: Spacing,
        ): Punct {
            require(ch in LEGAL_CHARS) { "unsupported character `$ch`" }
            return Punct(
                PunctData(
                    ch = ch,
                    joint = spacing == Spacing.JOINT,
                    span = Span.callSite(),
                ),
            )
        }
    }

    /** Returns an independent copy of this [Punct]. */
    public fun clone(): Punct =
        Punct(
            PunctData(
                ch = data.ch,
                joint = data.joint,
                span = data.span,
            ),
        )

    /** Returns the value of this punctuation character as [Char]. */
    public fun asChar(): Char = data.ch

    /**
     * Returns the spacing of this punctuation character, indicating whether
     * it can potentially combine into a multi-character operator with the
     * following token ([Spacing.JOINT]) or whether the operator has ended
     * ([Spacing.ALONE]).
     */
    public fun spacing(): Spacing = if (data.joint) Spacing.JOINT else Spacing.ALONE

    /** Returns the span for this punctuation character. */
    public fun span(): Span = data.span

    /** Configures the span for this punctuation character. */
    public fun setSpan(span: Span) {
        data.span = span
    }

    /** Compares this punctuation token against a single [Char]. */
    public fun eq(rhs: Char): Boolean = asChar() == rhs

    /** Prints the punctuation character as a single-character string. */
    override fun toString(): String = asChar().toString()
}

/**
 * Internal backing store for [Punct].
 */
internal data class PunctData(
    val ch: Char,
    val joint: Boolean,
    var span: Span,
)
