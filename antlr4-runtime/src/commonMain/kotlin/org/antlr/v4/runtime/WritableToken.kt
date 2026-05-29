/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

interface WritableToken : Token {
    override var text: String?
    override var type: Int
    override var line: Int
    override var charPositionInLine: Int
    override var channel: Int
    override var tokenIndex: Int
}
