// port-lint: source src/to_tokens.rs
package io.github.kotlinmania.procmacro

private class PrimitiveToTokens(
    private val emit: (TokenStream) -> Unit,
) : ToTokens {
    override fun toTokens(tokens: TokenStream): Unit = emit(tokens)
}

public fun UByte.asU8ToTokens(): ToTokens =
    PrimitiveToTokens { tokens ->
        Literal.u8Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun UShort.asU16ToTokens(): ToTokens =
    PrimitiveToTokens { tokens ->
        Literal.u16Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun UInt.asU32ToTokens(): ToTokens =
    PrimitiveToTokens { tokens ->
        Literal.u32Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun ULong.asU64ToTokens(): ToTokens =
    PrimitiveToTokens { tokens ->
        Literal.u64Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun Byte.asI8ToTokens(): ToTokens =
    PrimitiveToTokens { tokens ->
        Literal.i8Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun Short.asI16ToTokens(): ToTokens =
    PrimitiveToTokens { tokens ->
        Literal.i16Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun Int.asI32ToTokens(): ToTokens =
    PrimitiveToTokens { tokens ->
        Literal.i32Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun Long.asI64ToTokens(): ToTokens =
    PrimitiveToTokens { tokens ->
        Literal.i64Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun Float.asF32ToTokens(): ToTokens =
    PrimitiveToTokens { tokens ->
        Literal.f32Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun Double.asF64ToTokens(): ToTokens =
    PrimitiveToTokens { tokens ->
        Literal.f64Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun Boolean.asBoolToTokens(): ToTokens =
    PrimitiveToTokens { tokens ->
        val word =
            if (this) {
                "true"
            } else {
                "false"
            }
        Ident.new(word, Span.callSite()).asToTokens().toTokens(tokens)
    }

public fun Char.asCharToTokens(): ToTokens =
    PrimitiveToTokens { tokens ->
        Literal.character(this).asToTokens().toTokens(tokens)
    }

public fun String.asStringToTokens(): ToTokens =
    PrimitiveToTokens { tokens ->
        Literal.string(this).asToTokens().toTokens(tokens)
    }

public fun ULong.asUsizeToTokens(): ToTokens =
    PrimitiveToTokens { tokens ->
        Literal.usizeSuffixed(this).asToTokens().toTokens(tokens)
    }

public fun Long.asIsizeToTokens(): ToTokens =
    PrimitiveToTokens { tokens ->
        Literal.isizeSuffixed(this).asToTokens().toTokens(tokens)
    }
