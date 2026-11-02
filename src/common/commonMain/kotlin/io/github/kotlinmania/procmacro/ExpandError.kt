// port-lint: source src/lib.rs
package io.github.kotlinmania.procmacro

/**
 * Error returned from [TokenStream.expandExpr].
 */
public class ExpandError internal constructor() {
    override fun toString(): String = "macro expansion failed"
}
