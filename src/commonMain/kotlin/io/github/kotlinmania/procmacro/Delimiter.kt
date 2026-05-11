// port-lint: source src/lib.rs
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
     * `∅ ... ∅`
     *
     * An invisible delimiter, that may, for example, appear around tokens
     * coming from a "macro variable" `$var`. It is important to preserve
     * operator priorities in cases like `$var * 3` where `$var` is `1 + 2`.
     * Invisible delimiters might not survive roundtrip of a token stream
     * through a string.
     *
     * Note: rustc currently can ignore the grouping of tokens delimited by
     * `NONE` in the output of a proc_macro. Only `NONE`-delimited groups
     * created by a declarative macro in the input of a proc_macro macro
     * are preserved, and only in very specific circumstances. Any
     * `NONE`-delimited groups (re)created by a proc-macro will therefore
     * not preserve operator priorities as indicated above. The other
     * `Delimiter` variants should be used instead in this context. This
     * is a rustc bug. For details, see
     * [rust-lang/rust#67062](https://github.com/rust-lang/rust/issues/67062).
     */
    NONE,
}
