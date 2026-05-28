/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

import org.antlr.v4.runtime.atn.ATN
import org.antlr.v4.runtime.atn.ATNType
import org.antlr.v4.runtime.atn.LexerATNSimulator
import org.antlr.v4.runtime.atn.PredictionContextCache
import org.antlr.v4.runtime.dfa.DFA

class LexerInterpreter(
    grammarFileName: String?,
    vocabulary: Vocabulary,
    ruleNames: Collection<String?>,
    channelNames: Collection<String?>,
    modeNames: Collection<String?>,
    atn: ATN,
    input: CharStream?,
) : Lexer(input) {
    override val grammarFileName: String
    override val atn: ATN

    @get:Deprecated
    @Deprecated
    override val tokenNames: Array<String?>
    override val ruleNames: Array<String?>?
    override val channelNames: Array<String?>?
    override val modeNames: Array<String?>?

    private val vocabulary: Vocabulary?

    protected val _decisionToDFA: Array<DFA?>
    protected val _sharedContextCache: PredictionContextCache = PredictionContextCache()

    @Deprecated
    constructor(
        grammarFileName: String?,
        tokenNames: Collection<String?>,
        ruleNames: Collection<String?>,
        modeNames: Collection<String?>,
        atn: ATN,
        input: CharStream?,
    ) : this(
        grammarFileName,
        VocabularyImpl.fromTokenNames(tokenNames.toList().toTypedArray()),
        ruleNames,
        ArrayList<String?>(),
        modeNames,
        atn,
        input,
    )

    @Deprecated
    constructor(
        grammarFileName: String?,
        vocabulary: Vocabulary,
        ruleNames: Collection<String?>,
        modeNames: Collection<String?>,
        atn: ATN,
        input: CharStream?,
    ) : this(grammarFileName, vocabulary, ruleNames, ArrayList<String?>(), modeNames, atn, input)

    init {
        require(atn.grammarType === ATNType.LEXER) { "The ATN must be a lexer ATN." }

        this.grammarFileName = grammarFileName
        this.atn = atn
        this.tokenNames = arrayOfNulls<String>(atn.maxTokenType)
        for (i in tokenNames.indices) {
            tokenNames[i] = vocabulary.getDisplayName(i)
        }

        this.ruleNames = ruleNames.toList().toTypedArray()
        this.channelNames = channelNames.toList().toTypedArray()
        this.modeNames = modeNames.toList().toTypedArray()
        this.vocabulary = vocabulary

        this._decisionToDFA = arrayOfNulls<DFA>(atn.numberOfDecisions)
        for (i in _decisionToDFA.indices) {
            _decisionToDFA[i] = DFA(atn.getDecisionState(i), i)
        }
        this.interpreter = LexerATNSimulator(this, atn, _decisionToDFA, _sharedContextCache)
    }

    override val atn: ATN
        get() = atn

    fun getVocabulary(): Vocabulary? {
        if (vocabulary != null) {
            return vocabulary
        }

        return super.vocabulary
    }
}
