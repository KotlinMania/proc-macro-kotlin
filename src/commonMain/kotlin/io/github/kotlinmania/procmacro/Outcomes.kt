// port-lint: source src/lib.rs
package io.github.kotlinmania.procmacro

public sealed class TokenStreamParseOutcome {
    public data class Ok(
        public val value: TokenStream,
    ) : TokenStreamParseOutcome()

    public data class Err(
        public val error: LexError,
    ) : TokenStreamParseOutcome()

    public val isSuccess: Boolean get() = this is Ok
    public val isFailure: Boolean get() = this is Err

    public fun errorOrNull(): LexError? = (this as? Err)?.error

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

    public fun errorOrNull(): ExpandError? = (this as? Err)?.error

    public fun getOrThrow(): TokenStream =
        when (this) {
            is Ok -> value
            is Err -> error("TokenStream expansion failed: $error")
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

    public fun errorOrNull(): ConversionErrorKind? = (this as? Err)?.error

    public fun exceptionOrNull(): ConversionErrorKind? = errorOrNull()

    public fun getOrThrow(): UByte =
        when (this) {
            is Ok -> value
            is Err -> error("literal conversion failed: $error")
        }
}

public sealed class CharacterValueOutcome {
    public data class Ok(
        public val value: Char,
    ) : CharacterValueOutcome()

    public data class Err(
        public val error: ConversionErrorKind,
    ) : CharacterValueOutcome()

    public val isSuccess: Boolean get() = this is Ok
    public val isFailure: Boolean get() = this is Err

    public fun errorOrNull(): ConversionErrorKind? = (this as? Err)?.error

    public fun exceptionOrNull(): ConversionErrorKind? = errorOrNull()

    public fun getOrThrow(): Char =
        when (this) {
            is Ok -> value
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

    public fun errorOrNull(): ConversionErrorKind? = (this as? Err)?.error

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

    public fun errorOrNull(): ConversionErrorKind? = (this as? Err)?.error

    public fun exceptionOrNull(): ConversionErrorKind? = errorOrNull()

    public fun getOrThrow(): ByteArray =
        when (this) {
            is Ok -> value
            is Err -> error("literal conversion failed: $error")
        }
}
