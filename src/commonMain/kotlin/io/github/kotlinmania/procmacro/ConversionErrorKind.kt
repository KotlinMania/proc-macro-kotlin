// port-lint: source src/lib.rs
package io.github.kotlinmania.procmacro

/**
 * Errors returned when trying to retrieve a literal unescaped value.
 */
public sealed class ConversionErrorKind {
    /**
     * The literal failed to be escaped. See [EscapeError] for more
     * information.
     */
    public data class FailedToUnescape(public val error: EscapeError) : ConversionErrorKind()

    /** Trying to convert a literal with the wrong type. */
    public data object InvalidLiteralKind : ConversionErrorKind()
}

/**
 * Errors that can occur while unescaping a literal's source representation.
 *
 * The Kotlin port mirrors the surface that upstream re-exports from
 * `rustc_literal_escaper::EscapeError`. The Phase 3 KotlinLexer-backed
 * implementation will populate the variants downstream callers actually
 * observe; for now this sealed class exists so [ConversionErrorKind] has
 * the right shape.
 */
public sealed class EscapeError {
    /** The unescape pass found a syntactic error and could not continue. */
    public data class Fatal(public val description: String) : EscapeError()

    /**
     * The unescape pass found an issue that is recoverable; the caller may
     * choose to keep the partial result or report the error.
     */
    public data class Recoverable(public val description: String) : EscapeError()

    /** Returns whether this error stops further unescaping. */
    public fun isFatal(): Boolean = this is Fatal
}
