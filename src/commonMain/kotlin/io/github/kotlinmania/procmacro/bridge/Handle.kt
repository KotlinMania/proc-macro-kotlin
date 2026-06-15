// port-lint: source src/bridge/handle.rs
package io.github.kotlinmania.procmacro.bridge

import kotlin.jvm.JvmInline

@JvmInline
internal value class Handle(
    val raw: Int,
) {
    init {
        require(raw > 0) { "bridge handles are one-based" }
    }
}

internal class OwnedStore<T> {
    private var next = 1
    private val values: MutableMap<Handle, T> = linkedMapOf()

    fun alloc(value: T): Handle {
        val handle = Handle(next++)
        values[handle] = value
        return handle
    }

    fun take(handle: Handle): T =
        values.remove(handle)
            ?: throw IllegalArgumentException("unknown owned bridge handle ${handle.raw}")

    operator fun get(handle: Handle): T =
        values[handle]
            ?: throw IllegalArgumentException("unknown owned bridge handle ${handle.raw}")
}

internal class InternedStore<T> {
    private var next = 1
    private val handleToValue: MutableMap<Handle, T> = linkedMapOf()
    private val valueToHandle: MutableMap<T, Handle> = linkedMapOf()

    fun alloc(value: T): Handle =
        valueToHandle.getOrPut(value) {
            val handle = Handle(next++)
            handleToValue[handle] = value
            handle
        }

    fun copy(handle: Handle): T =
        handleToValue[handle]
            ?: throw IllegalArgumentException("unknown interned bridge handle ${handle.raw}")
}
