package io.github.kotlinmania.procmacro

import io.github.kotlinmania.rustcliteralescaper.EscapeError

/**
 * Errors returned when trying to retrieve an unescaped literal value.
 *
 * Public APIs return concrete outcome types so Swift Export does not have
 * to bridge exceptions across module boundaries.
 */
public sealed class ConversionErrorKind {
    /**
     * The literal failed to be unescaped. See [EscapeError] for details.
     */
    public class FailedToUnescape(
        public val error: EscapeError,
    ) : ConversionErrorKind() {
        override fun equals(other: Any?): Boolean = other is FailedToUnescape && other.error == error

        override fun hashCode(): Int = error.hashCode()

        override fun toString(): String = "FailedToUnescape($error)"
    }

    /** Trying to convert a literal with the wrong literal kind. */
    public object InvalidLiteralKind : ConversionErrorKind()
}
