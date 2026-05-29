/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc

class DoubleKeyMap<Key1, Key2, Value> {
    var data: MutableMap<Key1, MutableMap<Key2, Value>> = LinkedHashMap()

    fun put(
        k1: Key1,
        k2: Key2,
        v: Value,
    ): Value? {
        var data2 = data[k1]
        var prev: Value? = null
        if (data2 == null) {
            data2 = LinkedHashMap()
            data[k1] = data2
        } else {
            prev = data2[k2]
        }
        data2[k2] = v
        return prev
    }

    fun get(
        k1: Key1,
        k2: Key2,
    ): Value? {
        val data2 = data[k1] ?: return null
        return data2[k2]
    }

    fun get(k1: Key1): Map<Key2, Value> = data[k1] ?: emptyMap()

    fun values(k1: Key1): Collection<Value>? {
        val data2 = data[k1] ?: return null
        return data2.values
    }

    fun keySet(): Set<Key1> = data.keys

    fun keySet(k1: Key1): Set<Key2>? {
        val data2 = data[k1] ?: return null
        return data2.keys
    }
}
