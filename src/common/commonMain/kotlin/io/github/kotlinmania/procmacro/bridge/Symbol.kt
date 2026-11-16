// port-lint: source src/bridge/symbol.rs
package io.github.kotlinmania.procmacro.bridge

import io.github.kotlinmania.threadlocal.ThreadLocal
import kotlin.jvm.JvmInline

/**
 * Handle for a symbol string stored within the thread-local [Interner].
 */
@JvmInline
internal value class Symbol internal constructor(
    internal val id: Int,
) {
    init {
        require(id > 0) { "symbol handle must be non-zero" }
    }

    /**
     * Runs [f] with this symbol's interned string value, failing if the
     * symbol interner was cleared since this symbol was created.
     */
    fun <R> with(f: (String) -> R): R = f(threadInterner.get().get(this))

    fun asString(): String = with { it }

    fun encode(w: Buffer) {
        with { text -> RpcCodec.encodeString(w, text) }
    }

    override fun toString(): String = asString()

    companion object {
        private val rawIdentDisallowed = setOf("_", "self", "super", "Self", "crate", "\$crate")

        /** Interns [string] into the thread-local symbol store. */
        fun new(string: String): Symbol = threadInterner.get().intern(string)

        /** Alias for [new] used by existing bridge call sites. */
        fun intern(value: String): Symbol = new(value)

        /**
         * Validates and interns an identifier symbol, delegating non-ASCII
         * identifiers to the active bridge when necessary.
         */
        fun newIdent(
            string: String,
            isRaw: Boolean,
        ): Symbol {
            if (isValidAsciiIdent(string.encodeToByteArray()) || string == "\$crate") {
                require(!isRaw || canBeRaw(string)) { "`$string` cannot be a raw identifier" }
                return new(string)
            }
            if (string.all { it.code < 0x80 }) {
                throw IllegalArgumentException("`$string` is not a valid identifier")
            }
            val validated =
                if (isRaw) {
                    normalizeAndValidateRawIdent(string)
                } else {
                    BridgeMethods.symbolNormalizeAndValidateIdent(string)
                }
            return when (validated) {
                is Result.Ok -> validated.value
                is Result.Err -> throw IllegalArgumentException("`$string` is not a valid identifier")
            }
        }

        /**
         * Clears the thread-local symbol interner, invalidating all symbols
         * previously allocated on the current thread.
         */
        fun invalidateAll() {
            threadInterner.get().clear()
        }

        /**
         * Fast-path check for valid ASCII identifiers without RPC overhead.
         */
        fun isValidAsciiIdent(bytes: ByteArray): Boolean {
            if (bytes.isEmpty()) return false
            val first = bytes[0].toInt() and 0xff
            val validStart =
                first == '_'.code ||
                    first in 'a'.code..'z'.code ||
                    first in 'A'.code..'Z'.code
            if (!validStart) return false
            for (i in 1 until bytes.size) {
                val b = bytes[i].toInt() and 0xff
                val validContinue =
                    b == '_'.code ||
                        b in 'a'.code..'z'.code ||
                        b in 'A'.code..'Z'.code ||
                        b in '0'.code..'9'.code
                if (!validContinue) return false
            }
            return true
        }

        /** Returns whether [string] is permitted as a raw identifier. */
        fun canBeRaw(string: String): Boolean = string !in rawIdentDisallowed

        fun normalizeAndValidateIdent(string: String): Result<Symbol> {
            if (!isValidIdent(string, raw = false)) return Result.Err("invalid identifier")
            return Result.Ok(new(string))
        }

        fun normalizeAndValidateRawIdent(string: String): Result<Symbol> {
            if (!isValidIdent(string, raw = true)) return Result.Err("invalid raw identifier")
            return Result.Ok(new(string))
        }

        fun decode(r: BufferReader): Symbol = new(RpcCodec.decodeString(r))

        private fun isValidIdent(
            string: String,
            raw: Boolean,
        ): Boolean {
            if (string == "\$crate") return !raw
            if (string.isEmpty()) return false
            val first = string[0]
            if (!first.isLetter() && first != '_') return false
            for (i in 1 until string.length) {
                val ch = string[i]
                if (!ch.isLetterOrDigit() && ch != '_') return false
            }
            return !raw || canBeRaw(string)
        }
    }
}

/**
 * Arena-backed symbol interner using [FxHashMap] and an advancing base offset
 * so invalidated symbol handles cannot alias newly interned strings.
 */
internal class Interner(
    private var arena: Arena = Arena.new(),
    private val names: FxHashMap<String, Symbol> = FxHashMap.default(),
    private val strings: MutableList<String> = mutableListOf(),
    private var symBase: Int = 1,
) {
    fun intern(string: String): Symbol {
        names[string]?.let { return it }
        val nextId = symBase.toLong() + strings.size.toLong()
        check(nextId in 1..Int.MAX_VALUE.toLong()) { "symbol interner overflow" }
        val symbol = Symbol(nextId.toInt())
        val stored = arena.allocStr(string)
        strings.add(stored)
        names[stored] = symbol
        return symbol
    }

    fun get(symbol: Symbol): String {
        val index = symbol.id - symBase
        check(index in 0 until strings.size) { "use-after-free of invalidated symbol ${symbol.id}" }
        return strings[index]
    }

    fun clear() {
        val advanced = symBase.toLong() + strings.size.toLong()
        symBase = advanced.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        names.clear()
        strings.clear()
        arena = Arena.new()
    }
}

private class ThreadInternerHolder {
    private val local: ThreadLocal<Interner> = ThreadLocal()

    fun get(): Interner = local.getOr { Interner() }
}

private val threadInterner = ThreadInternerHolder()
