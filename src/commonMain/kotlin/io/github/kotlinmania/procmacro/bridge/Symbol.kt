// port-lint: source src/bridge/symbol.rs
package io.github.kotlinmania.procmacro.bridge

import kotlin.jvm.JvmInline

@JvmInline
internal value class Symbol private constructor(
    private val value: String,
) {
    fun asString(): String = value

    companion object {
        private val rawIdentDisallowed = setOf("_", "self", "super", "Self", "crate")

        fun intern(value: String): Symbol = Symbol(value)

        fun normalizeAndValidateIdent(string: String): Result<Symbol> {
            if (!isValidIdent(string, raw = false)) return Result.Err("invalid identifier")
            return Result.Ok(Symbol(string))
        }

        fun normalizeAndValidateRawIdent(string: String): Result<Symbol> {
            if (!isValidIdent(string, raw = true)) return Result.Err("invalid raw identifier")
            return Result.Ok(Symbol(string))
        }

        private fun isValidIdent(
            string: String,
            raw: Boolean,
        ): Boolean {
            if (string.isEmpty()) return false
            val first = string[0]
            if (!first.isLetter() && first != '_') return false
            for (i in 1 until string.length) {
                val ch = string[i]
                if (!ch.isLetterOrDigit() && ch != '_') return false
            }
            return !raw || string !in rawIdentDisallowed
        }
    }
}
