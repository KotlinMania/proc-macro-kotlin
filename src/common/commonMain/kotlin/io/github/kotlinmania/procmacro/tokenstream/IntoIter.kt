package io.github.kotlinmania.procmacro.tokenstream

import io.github.kotlinmania.procmacro.TokenTree

/**
 * An iterator over [io.github.kotlinmania.procmacro.TokenStream] elements.
 * Iteration is shallow and returns delimited groups as whole [TokenTree]
 * values without recursing into them.
 */
public class IntoIter internal constructor(
    private val items: List<TokenTree>,
    private var cursor: Int = 0,
) : Iterator<TokenTree> {
    internal constructor(source: Iterator<TokenTree>) : this(source.asSequence().toList(), 0)

    override fun hasNext(): Boolean = cursor < items.size

    override fun next(): TokenTree {
        if (cursor >= items.size) {
            throw NoSuchElementException("TokenStream iterator exhausted")
        }
        return items[cursor++]
    }

    /** Returns an independent iterator positioned at the same element. */
    public fun clone(): IntoIter = IntoIter(items.map { it.clone() }, cursor)

    /** Returns the number of remaining unconsumed token trees. */
    public fun remainingCount(): Int = items.size - cursor
}
