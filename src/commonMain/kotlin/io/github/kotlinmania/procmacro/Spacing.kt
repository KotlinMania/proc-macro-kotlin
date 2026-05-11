// port-lint: source src/lib.rs
package io.github.kotlinmania.procmacro

/**
 * Indicates whether a [Punct] token can join with the following token
 * to form a multi-character operator.
 */
public enum class Spacing {
    /**
     * A [Punct] token can join with the following token to form a
     * multi-character operator.
     *
     * In token streams constructed using proc macro interfaces, [JOINT]
     * punctuation tokens can be followed by any other tokens. However, in
     * token streams parsed from source code, the compiler will only set
     * spacing to [JOINT] in the following cases.
     *  - When a [Punct] is immediately followed by another [Punct] without
     *    a whitespace. E.g. `+` is [JOINT] in `+=` and `++`.
     *  - When a single quote `'` is immediately followed by an identifier
     *    without a whitespace. E.g. `'` is [JOINT] in `'lifetime`.
     *
     * This list may be extended in the future to enable more token
     * combinations.
     */
    JOINT,

    /**
     * A [Punct] token cannot join with the following token to form a
     * multi-character operator.
     *
     * [ALONE] punctuation tokens can be followed by any other tokens. In
     * token streams parsed from source code, the compiler will set
     * spacing to [ALONE] in all cases not covered by the conditions for
     * [JOINT] above. E.g. `+` is [ALONE] in `+ =`, `+ident` and `+()`. In
     * particular, tokens not followed by anything will be marked as
     * [ALONE].
     */
    ALONE,
}
