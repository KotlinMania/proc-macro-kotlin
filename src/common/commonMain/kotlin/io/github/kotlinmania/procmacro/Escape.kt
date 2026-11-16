// port-lint: source src/escape.rs
package io.github.kotlinmania.procmacro

import io.github.kotlinmania.procmacro.rustcore.appendUnicodeScalar
import io.github.kotlinmania.procmacro.rustcore.escapeAscii
import io.github.kotlinmania.procmacro.rustcore.escapeDebug
import io.github.kotlinmania.procmacro.rustcore.unicodeScalars
import io.github.kotlinmania.procmacro.rustcore.utf8Chunks

internal data class EscapeOptions(
    val escapeSingleQuote: Boolean,
    val escapeDoubleQuote: Boolean,
    val escapeNonAscii: Boolean,
)

internal fun escapeBytes(
    bytes: ByteArray,
    opt: EscapeOptions,
): String {
    val repr = StringBuilder()

    if (opt.escapeNonAscii) {
        for (byte in bytes) {
            escapeSingleByte(byte, opt, repr)
        }
    } else {
        for (chunk in utf8Chunks(bytes)) {
            for (ch in unicodeScalars(chunk.valid)) {
                escapeSingleChar(ch, opt, repr)
            }
            for (byte in chunk.invalid) {
                escapeSingleByte(byte, opt, repr)
            }
        }
    }

    return repr.toString()
}

private fun escapeSingleByte(
    byte: Byte,
    opt: EscapeOptions,
    repr: StringBuilder,
) {
    val u = byte.toInt() and 0xff
    if (u == 0x00) {
        repr.append("\\0")
    } else if ((u == 0x27 && !opt.escapeSingleQuote) ||
        (u == 0x22 && !opt.escapeDoubleQuote)
    ) {
        repr.append(u.toChar())
    } else {
        escapeAscii(u, repr)
    }
}

private fun escapeSingleChar(
    ch: Int,
    opt: EscapeOptions,
    repr: StringBuilder,
) {
    if ((ch == 0x27 && !opt.escapeSingleQuote) ||
        (ch == 0x22 && !opt.escapeDoubleQuote)
    ) {
        repr.appendUnicodeScalar(ch)
    } else {
        escapeDebug(ch, repr)
    }
}
