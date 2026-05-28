/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.misc.MurmurHash

/**
 * Implements the `more` lexer action by calling [Lexer.more].
 *
 *
 * The `more` command does not have any parameters, so this action is
 * implemented as a singleton instance exposed by [.INSTANCE].
 *
 * @author Sam Harwell
 * @since 4.2
 */
class LexerMoreAction
/**
     * Constructs the singleton instance of the lexer `more` command.
     */
    private constructor() :
    LexerAction {
        val actionType: LexerActionType
            /**
             * {@inheritDoc}
             * @return This method returns [LexerActionType.MORE].
             */
            get() = LexerActionType.MORE
        val isPositionDependent: Boolean
            /**
             * {@inheritDoc}
             * @return This method returns `false`.
             */
            get() = false

        /**
         * {@inheritDoc}
         *
         *
         * This action is implemented by calling [Lexer.more].
         */
        override fun execute(lexer: Lexer) {
            lexer.more()
        }

        override fun hashCode(): Int {
            var hash: Int = MurmurHash.initialize()
            hash = MurmurHash.update(hash, this.actionType.ordinal)
            return MurmurHash.finish(hash, 1)
        }

        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        override fun equals(obj: Any?): Boolean = obj === this

        override fun toString(): String = "more"

        companion object {
            /**
             * Provides a singleton instance of this parameterless lexer action.
             */
            val INSTANCE: LexerMoreAction = LexerMoreAction()
        }
    }
