package org.antlr.v4.runtime

/**
 * Runtime assertion for ANTLR4 internal invariants.
 * Mirrors Java `assert` behavior - throws [IllegalStateException] on failure.
 */
inline fun assert(
    condition: Boolean,
    lazyMessage: () -> String = { "Assertion failed" },
) {
    if (!condition) {
        throw IllegalStateException(lazyMessage())
    }
}
