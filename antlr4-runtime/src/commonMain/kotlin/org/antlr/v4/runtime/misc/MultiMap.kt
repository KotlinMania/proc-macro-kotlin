package org.antlr.v4.runtime.misc

class MultiMap<K, V> {
    val data: MutableMap<K, MutableList<V>> = LinkedHashMap()

    fun map(key: K, value: V) {
        data.getOrPut(key) { ArrayList() }.add(value)
    }

    val pairs: List<Pair<K, V>>
        get() = data.flatMap { (key, values) -> values.map { value -> Pair(key, value) } }

    operator fun get(key: K): MutableList<V>? = data[key]
}
