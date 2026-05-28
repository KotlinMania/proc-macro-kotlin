/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

import org.antlr.v4.runtime.atn.ATNConfigSet
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.misc.Utils

class LexerNoViableAltException(
    lexer: Lexer?,
    input: CharStream?,
    /** Matching attempted at what input index?  */
    val startIndex: Int,
    deadEndConfigs: ATNConfigSet?,
) : RecognitionException(lexer, input, null) {
    /** Which configurations did we try at input.index() that couldn't match input.LA(1)?  */
    private val deadEndConfigs: ATNConfigSet?

    init {
        this.deadEndConfigs = deadEndConfigs
    }

    fun getDeadEndConfigs(): ATNConfigSet? = deadEndConfigs

    val inputStream: CharStream?
        get() = super.inputStream as CharStream?

    fun toString(): String {
        var symbol: String? = ""
        if (startIndex >= 0 && startIndex < this.inputStream.size()) {
            symbol = this.inputStream.getText(Interval.of(startIndex, startIndex))
            symbol = Utils.escapeWhitespace(symbol, false)
        }

        return "${org.antlr.v4.runtime.LexerNoViableAltException::class.simpleName!!}('$symbol')"
    }
}
