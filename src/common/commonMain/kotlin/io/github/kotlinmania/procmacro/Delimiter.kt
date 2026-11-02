package io.github.kotlinmania.procmacro

/**
 * Describes how a sequence of token trees is delimited.
 */
public enum class Delimiter {
    /** `( ... )` */
    PARENTHESIS,

    /** `{ ... }` */
    BRACE,

    /** `[ ... ]` */
    BRACKET,

    /**
     * Invisible delimiter that may appear around tokens coming from a macro
     * variable to preserve operator precedence in expressions. Invisible
     * delimiters might not survive a round-trip of a token stream through a
     * string.
     */
    NONE,
}
