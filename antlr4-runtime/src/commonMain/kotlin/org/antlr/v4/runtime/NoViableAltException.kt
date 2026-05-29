/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

import org.antlr.v4.runtime.atn.ATNConfigSet

/** Indicates that the parser could not decide which of two or more paths
 * to take based upon the remaining input. It tracks the starting token
 * of the offending input and also knows where the parser was
 * in the various paths when the error. Reported by reportNoViableAlternative()
 */
class NoViableAltException(
    recognizer: Parser?,
    input: TokenStream?,
    startToken: Token?,
    offendingToken: Token?,
    deadEndConfigs: ATNConfigSet?,
    ctx: ParserRuleContext?,
) : RecognitionException(recognizer, input, ctx) {
    /** Which configurations did we try at input.index() that couldn't match input.LT(1)?  */
    val deadEndConfigs: ATNConfigSet?

    /** The token object at the start index; the input stream might
     * not be buffering tokens so get a reference to it. (At the
     * time the error occurred, of course the stream needs to keep a
     * buffer all of the tokens but later we might not have access to those.)
     */
    val startToken: Token?

    constructor(recognizer: Parser) : this(
        recognizer,
        recognizer.tokenStream,
        recognizer.currentToken,
        recognizer.currentToken,
        null,
        recognizer._ctx,
    )

    init {
        this.deadEndConfigs = deadEndConfigs
        this.startToken = startToken
        this.offendingToken = offendingToken
    }
}
