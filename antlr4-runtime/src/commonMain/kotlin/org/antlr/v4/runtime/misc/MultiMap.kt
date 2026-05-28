package org.antlr.v4.runtime.misc

class MultiMap<K, V> : LinkedHashMap<K, MutableList<V>>() {
    fun map(key: K, value: V) {
        var elementsForKey = get(key)
        if (elementsForKey == null) {
            elementsForKey = ArrayList()
            super.put(key, elementsForKey)
        }
        elementsForKey.add(value)
    }

    val pairs: List<Pair<K, V>>
        get() {
            val pairs = mutableListOf<Pair<K, V>>()
            for (key in keys) {
                for (value in this[key]!!) {
                    pairs.add(Pair(key, value))
                }
            }
            return pairs
        }
}
