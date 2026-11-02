// port-lint: source src/bridge/handle.rs
package io.github.kotlinmania.procmacro.bridge

import kotlin.jvm.JvmInline

/**
 * Non-zero numeric handle identifying server-held token streams or spans.
 */
@JvmInline
internal value class Handle(
    val raw: Int,
) {
    init {
        require(raw > 0) { "bridge handles are one-based" }
    }

    fun encode(w: Buffer) {
        RpcCodec.encodeInt(w, raw)
    }

    companion object {
        fun new(raw: Int): Handle? = if (raw > 0) Handle(raw) else null

        fun decode(r: BufferReader): Handle {
            val decoded = RpcCodec.decodeInt(r)
            return checkNotNull(new(decoded)) { "decoded zero or negative bridge handle: $decoded" }
        }
    }
}

/**
 * Monotonic positive handle counter shared across server handle stores.
 */
internal class HandleCounter(
    initial: Int = 1,
) {
    private var current: Int = initial

    init {
        check(initial > 0) { "handle counter initial value must be positive" }
    }

    fun load(): Int = current

    fun fetchAdd(delta: Int = 1): Int {
        val previous = current
        val next = previous.toLong() + delta.toLong()
        check(next in 1..Int.MAX_VALUE.toLong()) { "bridge handle counter overflowed" }
        current = next.toInt()
        return previous
    }
}

/**
 * Store associating values of type [T] with unique numeric [Handle] keys.
 */
internal class OwnedStore<T>(
    private val counter: HandleCounter = HandleCounter(1),
) {
    private val values: MutableMap<Handle, T> = linkedMapOf()

    init {
        check(counter.load() > 0) { "handle counter must be non-zero" }
    }

    fun alloc(value: T): Handle {
        val raw = counter.fetchAdd(1)
        val handle = checkNotNull(Handle.new(raw)) { "bridge handle counter overflowed" }
        check(values.put(handle, value) == null) { "duplicate bridge handle ${handle.raw}" }
        return handle
    }

    fun take(handle: Handle): T =
        values.remove(handle)
            ?: throw IllegalArgumentException("unknown owned bridge handle ${handle.raw}")

    operator fun get(handle: Handle): T =
        values[handle]
            ?: throw IllegalArgumentException("unknown owned bridge handle ${handle.raw}")

    fun index(handle: Handle): T = get(handle)

    companion object {
        fun <T> new(counter: HandleCounter): OwnedStore<T> = OwnedStore(counter)
    }
}

/**
 * Deduplicating handle store that interns equal values of type [T] to the
 * same [Handle].
 */
internal class InternedStore<T>(
    private val owned: OwnedStore<T> = OwnedStore(),
    private val interner: FxHashMap<T, Handle> = FxHashMap.default(),
) {
    fun alloc(value: T): Handle =
        interner.getOrPut(value) {
            owned.alloc(value)
        }

    fun copy(handle: Handle): T = owned[handle]

    companion object {
        fun <T> new(counter: HandleCounter): InternedStore<T> =
            InternedStore(owned = OwnedStore.new(counter), interner = FxHashMap.default())
    }
}
