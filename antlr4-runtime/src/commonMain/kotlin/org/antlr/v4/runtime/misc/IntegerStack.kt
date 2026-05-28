/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc

/**
 *
 * @author Sam Harwell
 */
class IntStack : IntList {
    constructor()

    constructor(capacity: Int) : super(capacity)

    constructor(list: IntStack?) : super(list)

    fun push(value: Int) {
        add(value)
    }

    fun pop(): Int = removeAt(size() - 1)

    fun peek(): Int = get(size() - 1)
}
