// port-lint: source compiler/psi/parser/src/org/jetbrains/kotlin/parsing/LastBefore.java
package org.jetbrains.kotlin.kmp.parser

public class LastBefore(
    private val lookFor: TokenStreamPredicate,
    private val stopAt: TokenStreamPredicate,
    private val dontStopRightAfterOccurrence: Boolean = false,
) : AbstractTokenStreamPattern() {
    private var previousLookForResult: Boolean = false

    override fun processToken(offset: Int, topLevel: Boolean): Boolean {
        val lookForResult = lookFor.matching(topLevel)
        if (lookForResult) {
            lastOccurrence = offset
        }
        if (stopAt.matching(topLevel)) {
            if (topLevel && (!dontStopRightAfterOccurrence || !previousLookForResult)) {
                return true
            }
        }
        previousLookForResult = lookForResult
        return false
    }

    override fun reset() {
        super.reset()
        previousLookForResult = false
    }
}
