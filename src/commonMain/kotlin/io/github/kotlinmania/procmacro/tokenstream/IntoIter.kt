// port-lint: source src/lib.rs (mod token_stream)
package io.github.kotlinmania.procmacro.tokenstream

import io.github.kotlinmania.procmacro.TokenTree

/**
 * An iterator over [io.github.kotlinmania.procmacro.TokenStream]'s
 * [TokenTree]s. The iteration is "shallow", e.g., the iterator does not
 * recurse into delimited groups, and returns whole groups as token trees.
 */
public class IntoIter internal constructor(
    private val source: Iterator<TokenTree>,
) : Iterator<TokenTree> {
    override fun hasNext(): Boolean = source.hasNext()
    override fun next(): TokenTree = source.next()
}
