package io.github.kotlinmania.procmacro

import io.github.kotlinmania.procmacro.rustcore.appendUnicodeScalar

public sealed class TokenStreamParseOutcome {
    public data class Ok(
        public val value: TokenStream,
    ) : TokenStreamParseOutcome()

    public data class Err(
        public val error: LexError,
    ) : TokenStreamParseOutcome()

    public val isSuccess: Boolean get() = this is Ok
    public val isFailure: Boolean get() = this is Err

    public fun errorOrNull(): LexError? =
        when (this) {
            is Ok -> null
            is Err -> error
        }

    public fun getOrThrow(): TokenStream =
        when (this) {
            is Ok -> value
            is Err -> error("TokenStream parse failed: $error")
        }
}

public sealed class TokenStreamExpandOutcome {
    public data class Ok(
        public val value: TokenStream,
    ) : TokenStreamExpandOutcome()

    public data class Err(
        public val error: ExpandError,
    ) : TokenStreamExpandOutcome()

    public val isSuccess: Boolean get() = this is Ok
    public val isFailure: Boolean get() = this is Err

    public fun errorOrNull(): ExpandError? =
        when (this) {
            is Ok -> null
            is Err -> error
        }

    public fun getOrThrow(): TokenStream =
        when (this) {
            is Ok -> value
            is Err -> error("TokenStream expansion failed: $error")
        }
}

public sealed class LiteralParseOutcome {
    public data class Ok(
        public val value: Literal,
    ) : LiteralParseOutcome()

    public data class Err(
        public val error: LexError,
    ) : LiteralParseOutcome()

    public val isSuccess: Boolean get() = this is Ok
    public val isFailure: Boolean get() = this is Err

    public fun errorOrNull(): LexError? =
        when (this) {
            is Ok -> null
            is Err -> error
        }

    public fun getOrThrow(): Literal =
        when (this) {
            is Ok -> value
            is Err -> error("Literal parse failed: $error")
        }
}

public enum class TrackedEnvError {
    NOT_PRESENT,
    NOT_UNICODE,
}

public sealed class TrackedEnvVarOutcome {
    public data class Ok(
        public val value: String,
    ) : TrackedEnvVarOutcome()

    public data class Err(
        public val error: TrackedEnvError,
    ) : TrackedEnvVarOutcome()

    public val isSuccess: Boolean get() = this is Ok
    public val isFailure: Boolean get() = this is Err

    public fun errorOrNull(): TrackedEnvError? =
        when (this) {
            is Ok -> null
            is Err -> error
        }

    public fun getOrThrow(): String =
        when (this) {
            is Ok -> value
            is Err -> error("Tracked environment variable lookup failed: $error")
        }
}

public sealed class ByteCharacterValueOutcome {
    public data class Ok(
        public val value: UByte,
    ) : ByteCharacterValueOutcome()

    public data class Err(
        public val error: ConversionErrorKind,
    ) : ByteCharacterValueOutcome()

    public val isSuccess: Boolean get() = this is Ok
    public val isFailure: Boolean get() = this is Err

    public fun errorOrNull(): ConversionErrorKind? =
        when (this) {
            is Ok -> null
            is Err -> error
        }

    public fun exceptionOrNull(): ConversionErrorKind? = errorOrNull()

    public fun getOrThrow(): UByte =
        when (this) {
            is Ok -> value
            is Err -> error("literal conversion failed: $error")
        }
}

public sealed class CharacterValueOutcome {
    public data class Ok(
        public val codePoint: Int,
    ) : CharacterValueOutcome() {
        public constructor(value: Char) : this(value.code)

        public val isBmpChar: Boolean
            get() = codePoint in 0..Char.MAX_VALUE.code

        public val value: Char
            get() {
                check(isBmpChar) {
                    "Supplementary Unicode scalar $codePoint does not fit in a single UTF-16 Char"
                }
                return codePoint.toChar()
            }

        public fun asText(): String = buildString { appendUnicodeScalar(codePoint) }
    }

    public data class Err(
        public val error: ConversionErrorKind,
    ) : CharacterValueOutcome()

    public val isSuccess: Boolean get() = this is Ok
    public val isFailure: Boolean get() = this is Err

    public fun errorOrNull(): ConversionErrorKind? =
        when (this) {
            is Ok -> null
            is Err -> error
        }

    public fun exceptionOrNull(): ConversionErrorKind? = errorOrNull()

    public fun getOrThrow(): Char =
        when (this) {
            is Ok -> value
            is Err -> error("literal conversion failed: $error")
        }

    public fun codePointOrThrow(): Int =
        when (this) {
            is Ok -> codePoint
            is Err -> error("literal conversion failed: $error")
        }

    public fun textOrThrow(): String =
        when (this) {
            is Ok -> asText()
            is Err -> error("literal conversion failed: $error")
        }
}

public sealed class StringValueOutcome {
    public data class Ok(
        public val value: String,
    ) : StringValueOutcome()

    public data class Err(
        public val error: ConversionErrorKind,
    ) : StringValueOutcome()

    public val isSuccess: Boolean get() = this is Ok
    public val isFailure: Boolean get() = this is Err

    public fun errorOrNull(): ConversionErrorKind? =
        when (this) {
            is Ok -> null
            is Err -> error
        }

    public fun exceptionOrNull(): ConversionErrorKind? = errorOrNull()

    public fun getOrThrow(): String =
        when (this) {
            is Ok -> value
            is Err -> error("literal conversion failed: $error")
        }
}

public sealed class ByteArrayValueOutcome {
    public data class Ok(
        public val value: ByteArray,
    ) : ByteArrayValueOutcome()

    public data class Err(
        public val error: ConversionErrorKind,
    ) : ByteArrayValueOutcome()

    public val isSuccess: Boolean get() = this is Ok
    public val isFailure: Boolean get() = this is Err

    public fun errorOrNull(): ConversionErrorKind? =
        when (this) {
            is Ok -> null
            is Err -> error
        }

    public fun exceptionOrNull(): ConversionErrorKind? = errorOrNull()

    public fun getOrThrow(): ByteArray =
        when (this) {
            is Ok -> value
            is Err -> error("literal conversion failed: $error")
        }
}
