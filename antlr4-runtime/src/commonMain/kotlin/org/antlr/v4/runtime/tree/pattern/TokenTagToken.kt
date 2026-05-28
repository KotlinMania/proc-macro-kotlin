package org.antlr.v4.runtime.tree.pattern

import org.antlr.v4.runtime.CommonToken

class TokenTagToken(
    val tokenName: String?,
    type: Int,
    val label: String?
) : CommonToken(type) {

    constructor(tokenName: String?, type: Int) : this(tokenName, type, null)

    override val text: String?
        get() {
            if (label != null) return "<$label:$tokenName>"
            return "<$tokenName>"
        }

    override fun toString(): String = "$tokenName:$type"
}
