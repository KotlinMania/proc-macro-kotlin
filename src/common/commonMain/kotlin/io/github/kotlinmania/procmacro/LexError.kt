package io.github.kotlinmania.procmacro

/**
 * Error returned from [TokenStream.fromString] and [Literal.fromString].
 *
 * The contained error message is diagnostic text and is not guaranteed to
 * have a fixed format across versions.
 */
public class LexError internal constructor(
    internal val messageText: String,
) {
    override fun toString(): String = messageText
}
