// port-lint: source src/lib.rs
package io.github.kotlinmania.procmacro

/**
 * Errors returned when trying to retrieve a literal unescaped value.
 *
 * Inherits from [Throwable] so that the [kotlin.Result] returned by the
 * `Literal::*_value` methods can carry an instance directly. Upstream
 * Rust returns `Result<T, ConversionErrorKind>`; Kotlin's `Result<T>`
 * fixes the error type at [Throwable], so the conversion-error sealed
 * hierarchy slots in there directly rather than via a wrapper.
 */
public sealed class ConversionErrorKind : Throwable() {
    /**
     * The literal failed to be unescaped. See [EscapeError] for more
     * information.
     */
    public class FailedToUnescape(public val error: EscapeError) : ConversionErrorKind() {
        override fun equals(other: Any?): Boolean =
            other is FailedToUnescape && other.error == error
        override fun hashCode(): Int = error.hashCode()
        override fun toString(): String = "FailedToUnescape($error)"
    }

    /** Trying to convert a literal with the wrong type. */
    public object InvalidLiteralKind : ConversionErrorKind()
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
