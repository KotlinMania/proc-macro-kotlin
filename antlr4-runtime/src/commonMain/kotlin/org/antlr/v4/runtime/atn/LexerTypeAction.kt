/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.misc.MurmurHash

/**
 * Implements the `type` lexer action by calling [Lexer.setType]
 * with the assigned type.
 *
 * @author Sam Harwell
 * @since 4.2
 */
class LexerTypeAction
/**
 * Constructs a new `type` action with the specified token type value.
 * @param type The type to assign to the token using [Lexer.setType].
 */
(
    /**
     * Gets the type to assign to a token created by the lexer.
     * @return The type to assign to a token created by the lexer.
     */
    val type: Int,
) : LexerAction {
    val actionType: LexerActionType
        /**
         * {@inheritDoc}
         * @return This method returns [LexerActionType.TYPE].
         */
        get() = LexerActionType.TYPE
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
     * This action is implemented by calling [Lexer.setType] with the
     * value provided by [.getType].
     */
    override fun execute(lexer: Lexer) {
        lexer.setType(type)
    }

    override fun hashCode(): Int {
        var hash: Int = MurmurHash.initialize()
        hash = MurmurHash.update(hash, this.actionType.ordinal)
        hash = MurmurHash.update(hash, type)
        return MurmurHash.finish(hash, 2)
    }

    override fun equals(obj: Any): Boolean {
        if (obj === this) {
            return true
        } else if (obj !is LexerTypeAction) {
            return false
        }

        return type == obj.type
    }

    override fun toString(): String = String.format("type(%d)", type)
}
