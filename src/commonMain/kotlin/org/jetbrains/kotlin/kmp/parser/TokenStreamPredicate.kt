// port-lint: source compiler/psi/parser/src/org/jetbrains/kotlin/parsing/TokenStreamPredicate.java
package org.jetbrains.kotlin.kmp.parser

public fun interface TokenStreamPredicate {
    public fun matching(topLevel: Boolean): Boolean

    public infix fun or(other: TokenStreamPredicate): TokenStreamPredicate =
        TokenStreamPredicate { topLevel -> matching(topLevel) || other.matching(topLevel) }
}
