// port-lint: source compiler/psi/parser/src/org/jetbrains/kotlin/parsing/TokenStreamPattern.java
package org.jetbrains.kotlin.kmp.parser

import com.intellij.platform.syntax.SyntaxElementType

public interface TokenStreamPattern {
    public fun processToken(offset: Int, topLevel: Boolean): Boolean

    public fun result(): Int

    public fun isTopLevel(
        openAngleBrackets: Int,
        openBrackets: Int,
        openBraces: Int,
        openParentheses: Int,
    ): Boolean

    public fun handleUnmatchedClosing(token: SyntaxElementType): Boolean
}
