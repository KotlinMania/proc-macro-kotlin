// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

internal fun <K : Any, V : Any> newConcurrentMultiMap(): MultiplatformConcurrentMultiMap<K, V> =
    MultiplatformConcurrentMultiMapImpl()

internal fun <K : Any, V : Any> newConcurrentMap(): MultiplatformConcurrentMap<K, V> =
    SimpleMultiplatformConcurrentMap()

internal fun <V : Any> newConcurrentSet(): MutableSet<V> = mutableSetOf()

private class SimpleMultiplatformConcurrentMap<K : Any, V : Any> : MultiplatformConcurrentMap<K, V> {
    private val map: MutableMap<K, V> = linkedMapOf()

    override val size: Int
        get() = map.size

    override val keys: Set<K>
        get() = map.keys

    override fun computeIfAbsent(
        key: K,
        f: (K) -> V,
    ): V = map.getOrPut(key) { f(key) }

    override fun get(key: K): V? = map[key]

    override fun remove(key: K): V? = map.remove(key)

    override fun put(
        key: K,
        value: V,
    ): V? = map.put(key, value)

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is SimpleMultiplatformConcurrentMap<*, *> &&
            map == other.map

    override fun hashCode(): Int = map.hashCode()

    override fun toString(): String = map.toString()
}

private class MultiplatformConcurrentMultiMapImpl<K : Any, V : Any> : MultiplatformConcurrentMultiMap<K, V> {
    private val map: MultiplatformConcurrentMap<K, MutableSet<V>> = newConcurrentMap()

    override fun putValue(
        key: K,
        value: V,
    ) {
        map.computeIfAbsent(key) { newConcurrentSet() }.add(value)
    }

    override fun get(key: K): Set<V> = map[key] ?: emptySet()

    override fun remove(
        key: K,
        value: V,
    ) {
        map[key]?.remove(value)
    }

    override fun remove(key: K) {
        map.remove(key)
    }
}
