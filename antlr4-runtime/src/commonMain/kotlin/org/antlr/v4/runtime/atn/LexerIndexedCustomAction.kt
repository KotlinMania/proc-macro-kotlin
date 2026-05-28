/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.misc.MurmurHash

class LexerIndexedCustomAction(
    val offset: Int,
    val action: LexerAction,
) : LexerAction {
    override val actionType: LexerActionType
        get() = action.actionType

    override val isPositionDependent: Boolean
        get() = true

    override fun execute(lexer: Lexer?) {
        action.execute(lexer)
    }

    override fun hashCode(): Int {
        var hash = MurmurHash.initialize()
        hash = MurmurHash.update(hash, offset)
        hash = MurmurHash.update(hash, action)
        return MurmurHash.finish(hash, 2)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is LexerIndexedCustomAction) return false
        return offset == other.offset && action == other.action
    }
}
