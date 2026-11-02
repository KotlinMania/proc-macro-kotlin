// port-lint: source src/lib.rs
package io.github.kotlinmania.procmacro

/**
 * Error returned from [TokenStream.fromString].
 *
 * The contained error message is explicitly not guaranteed to be stable in
 * any way, and may change between Kotlin versions or across compilations.
 */
public class LexError internal constructor(
    internal val messageText: String,
) {
    override fun toString(): String = messageText
}
