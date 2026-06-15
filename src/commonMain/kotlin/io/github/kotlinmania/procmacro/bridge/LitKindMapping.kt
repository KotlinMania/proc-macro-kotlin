// port-lint: source src/bridge/mod.rs
package io.github.kotlinmania.procmacro.bridge

import io.github.kotlinmania.procmacro.LitKind

internal fun LitKind.toBridgeLitKind(): BridgeLitKind =
    when (this) {
        LitKind.BYTE -> BridgeLitKind.Byte
        LitKind.CHAR -> BridgeLitKind.Char
        LitKind.INTEGER -> BridgeLitKind.Integer
        LitKind.FLOAT -> BridgeLitKind.Float
        LitKind.STR -> BridgeLitKind.Str
        is LitKind.STR_RAW -> BridgeLitKind.StrRaw(numHashes)
        LitKind.BYTE_STR -> BridgeLitKind.ByteStr
        is LitKind.BYTE_STR_RAW -> BridgeLitKind.ByteStrRaw(numHashes)
        LitKind.C_STR -> BridgeLitKind.CStr
        is LitKind.C_STR_RAW -> BridgeLitKind.CStrRaw(numHashes)
        LitKind.ERR_WITH_GUAR -> BridgeLitKind.ErrWithGuar
    }

internal fun BridgeLitKind.toPublicLitKind(): LitKind =
    when (this) {
        BridgeLitKind.Byte -> LitKind.BYTE
        BridgeLitKind.Char -> LitKind.CHAR
        BridgeLitKind.Integer -> LitKind.INTEGER
        BridgeLitKind.Float -> LitKind.FLOAT
        BridgeLitKind.Str -> LitKind.STR
        is BridgeLitKind.StrRaw -> LitKind.STR_RAW(numHashes)
        BridgeLitKind.ByteStr -> LitKind.BYTE_STR
        is BridgeLitKind.ByteStrRaw -> LitKind.BYTE_STR_RAW(numHashes)
        BridgeLitKind.CStr -> LitKind.C_STR
        is BridgeLitKind.CStrRaw -> LitKind.C_STR_RAW(numHashes)
        BridgeLitKind.ErrWithGuar -> LitKind.ERR_WITH_GUAR
    }
