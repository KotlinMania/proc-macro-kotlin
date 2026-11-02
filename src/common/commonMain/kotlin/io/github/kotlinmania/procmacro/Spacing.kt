package io.github.kotlinmania.procmacro

/**
 * Indicates whether a [Punct] token can join with the following token
 * to form a multi-character operator.
 */
public enum class Spacing {
    /**
     * A [Punct] token can join with the following token to form a
     * multi-character operator, such as `+` in `+=` or `++`.
     */
    JOINT,

    /**
     * A [Punct] token cannot join with the following token to form a
     * multi-character operator, such as `+` in `+ =` or at the end of a
     * stream.
     */
    ALONE,
}
