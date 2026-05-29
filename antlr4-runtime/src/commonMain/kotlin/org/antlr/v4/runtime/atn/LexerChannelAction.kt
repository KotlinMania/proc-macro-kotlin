/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.misc.MurmurHash

/**
 * Implements the `channel` lexer action by calling
 * [Lexer.setChannel] with the assigned channel.
 *
 * @author Sam Harwell
 * @since 4.2
 */
class LexerChannelAction
/**
 * Constructs a new `channel` action with the specified channel value.
 * @param channel The channel value to pass to [Lexer.setChannel].
 */
(
    /**
     * Gets the channel to use for the [Token] created by the lexer.
     *
     * @return The channel to use for the [Token] created by the lexer.
     */
    val channel: Int,
) : LexerAction {
    override val actionType: LexerActionType
        /**
         * {@inheritDoc}
         * @return This method returns [LexerActionType.CHANNEL].
         */
        get() = LexerActionType.CHANNEL
    override val isPositionDependent: Boolean
        /**
         * {@inheritDoc}
         * @return This method returns `false`.
         */
        get() = false

    /**
     * {@inheritDoc}
     *
     *
     * This action is implemented by calling [Lexer.setChannel] with the
     * value provided by [.getChannel].
     */
    override fun execute(lexer: Lexer) {
        lexer.channel = channel
    }

    override fun hashCode(): Int {
        var hash: Int = MurmurHash.initialize()
        hash = MurmurHash.update(hash, this.actionType.ordinal)
        hash = MurmurHash.update(hash, channel)
        return MurmurHash.finish(hash, 2)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        } else if (other !is LexerChannelAction) {
            return false
        }

        return channel == other.channel
    }

    override fun toString(): String = "channel($channel)"
}
