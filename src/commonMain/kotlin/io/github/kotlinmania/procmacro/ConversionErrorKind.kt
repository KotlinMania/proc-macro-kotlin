// port-lint: source src/lib.rs
package io.github.kotlinmania.procmacro

/**
 * Errors returned when trying to retrieve a literal unescaped value.
 *
 * Mirrors upstream `Result<T, ConversionErrorKind>` error payloads.
 * Public APIs return concrete outcome types instead of `kotlin.Result`
 * so Swift Export does not have to bridge `Throwable`.
 */
public sealed class ConversionErrorKind {
    /**
     * The literal failed to be unescaped. See [EscapeError] for more
     * information.
     */
    public class FailedToUnescape(
        public val error: EscapeError,
    ) : ConversionErrorKind() {
        override fun equals(other: Any?): Boolean = other is FailedToUnescape && other.error == error

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
 * `rustc_literal_escaper::EscapeError`.
 */
public sealed class EscapeError {
    /** The unescape pass found a syntactic error and could not continue. */
    public data class Fatal(
        public val message: String,
    ) : EscapeError()

    /**
     * The unescape pass found an issue that is recoverable; the caller may
     * choose to keep the partial result or report the error.
     */
    public data class Recoverable(
        public val message: String,
    ) : EscapeError()

    /** Returns whether this error stops further unescaping. */
    public fun isFatal(): Boolean = this is Fatal
}
