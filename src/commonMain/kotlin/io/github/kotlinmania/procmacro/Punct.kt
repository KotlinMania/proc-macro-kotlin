// port-lint: source src/lib.rs
package io.github.kotlinmania.procmacro

/**
 * A [Punct] is a single punctuation character such as `+`, `-` or `#`.
 *
 * Multi-character operators like `+=` are represented as two instances of
 * [Punct] with different forms of [Spacing] returned.
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
         * The `ch` argument must be a valid punctuation character
         * permitted by the language, otherwise the function will throw.
         *
         * The returned [Punct] will have the default span of
         * [Span.callSite] which can be further configured with the
         * [setSpan] method below.
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

    /** Returns the value of this punctuation character as [Char]. */
    public fun asChar(): Char = data.ch

    /**
     * Returns the spacing of this punctuation character, indicating
     * whether it can be potentially combined into a multi-character
     * operator with the following token ([Spacing.JOINT]), or whether the
     * operator has definitely ended ([Spacing.ALONE]).
     */
    public fun spacing(): Spacing = if (data.joint) Spacing.JOINT else Spacing.ALONE

    /** Returns the span for this punctuation character. */
    public fun span(): Span = data.span

    /** Configure the span for this punctuation character. */
    public fun setSpan(span: Span) {
        data.span = span
    }

    /**
     * Equality against the underlying character. Mirrors upstream's
     * `impl PartialEq<char> for Punct` / `impl PartialEq<Punct> for char`.
     */
    public fun eq(rhs: Char): Boolean = asChar() == rhs

    /**
     * Prints the punctuation character as a string that should be
     * losslessly convertible back into the same character.
     */
    override fun toString(): String = asChar().toString()
}

/**
 * Internal backing store for [Punct].
 *
 * Upstream: `bridge::Punct<bridge::client::Span>`.
 */
internal data class PunctData(
    val ch: Char,
    val joint: Boolean,
    var span: Span,
)
