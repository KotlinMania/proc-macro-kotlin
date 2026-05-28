/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc


class Pair<A, B>(val a: A?, val b: B?) : Serializable {
    fun equals(obj: Any?): Boolean {
        if (obj === this) {
            return true
        } else if (obj !is Pair<*, *>) {
            return false
        }

        val other = obj
        return AnyEqualityComparator.INSTANCE.equals(a, other.a)
                && AnyEqualityComparator.INSTANCE.equals(b, other.b)
    }
    fun hashCode(): Int {
        var hash: Int = MurmurHash.initialize()
        hash = MurmurHash.update(hash, a)
        hash = MurmurHash.update(hash, b)
        return MurmurHash.finish(hash, 2)
    }
    fun toString(): String {
        return String.format("(%s, %s)", a, b)
    }
}
