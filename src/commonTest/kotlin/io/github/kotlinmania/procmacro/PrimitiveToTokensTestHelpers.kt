package io.github.kotlinmania.procmacro

private class TestFunctionalToTokens(
    private val emit: (TokenStream) -> Unit,
) : ToTokens {
    override fun toTokens(tokens: TokenStream): Unit = emit(tokens)
}

internal fun UByte.asU8ToTokens(): ToTokens =
    TestFunctionalToTokens { tokens ->
        Literal.u8Suffixed(this).asToTokens().toTokens(tokens)
    }

internal fun UShort.asU16ToTokens(): ToTokens =
    TestFunctionalToTokens { tokens ->
        Literal.u16Suffixed(this).asToTokens().toTokens(tokens)
    }

internal fun UInt.asU32ToTokens(): ToTokens =
    TestFunctionalToTokens { tokens ->
        Literal.u32Suffixed(this).asToTokens().toTokens(tokens)
    }

internal fun ULong.asU64ToTokens(): ToTokens =
    TestFunctionalToTokens { tokens ->
        Literal.u64Suffixed(this).asToTokens().toTokens(tokens)
    }

internal fun Byte.asI8ToTokens(): ToTokens =
    TestFunctionalToTokens { tokens ->
        Literal.i8Suffixed(this).asToTokens().toTokens(tokens)
    }

internal fun Short.asI16ToTokens(): ToTokens =
    TestFunctionalToTokens { tokens ->
        Literal.i16Suffixed(this).asToTokens().toTokens(tokens)
    }

internal fun Int.asI32ToTokens(): ToTokens =
    TestFunctionalToTokens { tokens ->
        Literal.i32Suffixed(this).asToTokens().toTokens(tokens)
    }

internal fun Long.asI64ToTokens(): ToTokens =
    TestFunctionalToTokens { tokens ->
        Literal.i64Suffixed(this).asToTokens().toTokens(tokens)
    }

internal fun Float.asF32ToTokens(): ToTokens =
    TestFunctionalToTokens { tokens ->
        Literal.f32Suffixed(this).asToTokens().toTokens(tokens)
    }

internal fun Double.asF64ToTokens(): ToTokens =
    TestFunctionalToTokens { tokens ->
        Literal.f64Suffixed(this).asToTokens().toTokens(tokens)
    }

internal fun Boolean.asBoolToTokens(): ToTokens =
    TestFunctionalToTokens { tokens ->
        val word = if (this) "true" else "false"
        Ident.new(word, Span.callSite()).asToTokens().toTokens(tokens)
    }

internal fun Char.asCharToTokens(): ToTokens =
    TestFunctionalToTokens { tokens ->
        Literal.character(this).asToTokens().toTokens(tokens)
    }

internal fun String.asStringToTokens(): ToTokens =
    TestFunctionalToTokens { tokens ->
        Literal.string(this).asToTokens().toTokens(tokens)
    }
