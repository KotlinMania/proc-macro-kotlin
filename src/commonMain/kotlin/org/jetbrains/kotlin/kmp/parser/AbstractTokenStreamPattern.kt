// port-lint: source compiler/psi/parser/src/org/jetbrains/kotlin/parsing/AbstractTokenStreamPattern.java
package org.jetbrains.kotlin.kmp.parser

import com.intellij.platform.syntax.SyntaxElementType

public abstract class AbstractTokenStreamPattern : TokenStreamPattern {
    protected var lastOccurrence: Int = -1

    protected fun fail() {
        lastOccurrence = -1
    }

    override fun result(): Int = lastOccurrence

    override fun isTopLevel(
        openAngleBrackets: Int,
        openBrackets: Int,
        openBraces: Int,
        openParentheses: Int,
    ): Boolean =
        openBraces == 0 &&
            openBrackets == 0 &&
            openParentheses == 0 &&
            openAngleBrackets == 0

    override fun handleUnmatchedClosing(token: SyntaxElementType): Boolean = false

    public open fun reset() {
        lastOccurrence = -1
    }
}
