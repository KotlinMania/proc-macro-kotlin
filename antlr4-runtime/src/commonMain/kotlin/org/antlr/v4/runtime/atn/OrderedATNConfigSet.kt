/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.atn

import org.antlr.v4.runtime.misc.AnyEqualityComparator

/**
 *
 * @author Sam Harwell
 */
class OrderedATNConfigSet : ATNConfigSet() {
    init {
        this.configLookup = org.antlr.v4.runtime.atn.OrderedATNConfigSet.LexerConfigHashSet()
    }

    class LexerConfigHashSet : AbstractConfigHashSet(AnyEqualityComparator.INSTANCE)
}
