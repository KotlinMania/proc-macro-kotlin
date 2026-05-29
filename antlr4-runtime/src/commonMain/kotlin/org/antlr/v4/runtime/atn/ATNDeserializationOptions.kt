/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

/**
 *
 * @author Sam Harwell
 */
class ATNDeserializationOptions {
    var isReadOnly: Boolean = false
        private set
    private var verifyATN: Boolean
    private var generateRuleBypassTransitions: Boolean

    constructor() {
        this.verifyATN = true
        this.generateRuleBypassTransitions = false
    }

    constructor(options: ATNDeserializationOptions) {
        this.verifyATN = options.verifyATN
        this.generateRuleBypassTransitions = options.generateRuleBypassTransitions
    }

    fun makeReadOnly() {
        this.isReadOnly = true
    }

    fun isVerifyATN(): Boolean = verifyATN

    fun setVerifyATN(verifyATN: Boolean) {
        throwIfReadOnly()
        this.verifyATN = verifyATN
    }

    fun isGenerateRuleBypassTransitions(): Boolean = generateRuleBypassTransitions

    fun setGenerateRuleBypassTransitions(generateRuleBypassTransitions: Boolean) {
        throwIfReadOnly()
        this.generateRuleBypassTransitions = generateRuleBypassTransitions
    }

    protected fun throwIfReadOnly() {
        check(!this.isReadOnly) { "The object is read only." }
    }

    companion object {
        val defaultOptions: ATNDeserializationOptions by lazy {
            val opts = ATNDeserializationOptions()
            opts.makeReadOnly()
            opts
        }
    }
}
