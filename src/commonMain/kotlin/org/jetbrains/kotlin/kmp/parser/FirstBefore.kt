// port-lint: source compiler/psi/parser/src/org/jetbrains/kotlin/parsing/FirstBefore.java
package org.jetbrains.kotlin.kmp.parser

public class FirstBefore(
    private val lookFor: TokenStreamPredicate,
    private val stopAt: TokenStreamPredicate,
) : AbstractTokenStreamPattern() {
    override fun processToken(offset: Int, topLevel: Boolean): Boolean {
        if (lookFor.matching(topLevel)) {
            lastOccurrence = offset
            return true
        }
        if (stopAt.matching(topLevel)) {
            return true
        }
        return false
    }
}
