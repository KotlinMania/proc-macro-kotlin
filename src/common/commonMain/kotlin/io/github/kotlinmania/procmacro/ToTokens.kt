// port-lint: source src/to_tokens.rs
package io.github.kotlinmania.procmacro

import io.github.kotlinmania.procmacro.bridge.cloneTokenTree

/**
 * Types that can be interpolated inside a quasiquote invocation.
 */
public interface ToTokens {
    /** Writes this value to the given [TokenStream]. */
    public fun toTokens(tokens: TokenStream)

    /**
     * Converts this value directly into a [TokenStream] object using [toTokens].
     */
    public fun toTokenStream(): TokenStream {
        val tokens = TokenStream.new()
        toTokens(tokens)
        return tokens
    }

    /**
     * Consumes or converts this value directly into a [TokenStream] object.
     */
    public fun intoTokenStream(): TokenStream = toTokenStream()
}

private class FunctionalToTokens(
    private val emit: (TokenStream) -> Unit,
) : ToTokens {
    override fun toTokens(tokens: TokenStream): Unit = emit(tokens)
}

private class TokenTreeToTokens(
    private val tree: TokenTree,
) : ToTokens {
    override fun toTokens(tokens: TokenStream) {
        tokens.extendTokenTrees(listOf(cloneTokenTree(tree)))
    }

    override fun intoTokenStream(): TokenStream = TokenStream.fromTokenTree(tree)
}

/** Adapts a [TokenTree] as a [ToTokens] emitter. */
public fun TokenTree.asToTokens(): ToTokens = TokenTreeToTokens(this)

private class TokenStreamToTokens(
    private val stream: TokenStream,
) : ToTokens {
    override fun toTokens(tokens: TokenStream) {
        tokens.extendTokenTrees(stream.map(::cloneTokenTree))
    }

    override fun intoTokenStream(): TokenStream = stream
}

/** Adapts a [TokenStream] as a [ToTokens] emitter. */
public fun TokenStream.asToTokens(): ToTokens = TokenStreamToTokens(this)

/** Adapts a [Literal] as a [ToTokens] emitter. */
public fun Literal.asToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        tokens.extendTokenTrees(listOf(cloneTokenTree(TokenTree.Literal(this))))
    }

/** Adapts an [Ident] as a [ToTokens] emitter. */
public fun Ident.asToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        tokens.extendTokenTrees(listOf(cloneTokenTree(TokenTree.Ident(this))))
    }

/** Adapts a [Punct] as a [ToTokens] emitter. */
public fun Punct.asToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        tokens.extendTokenTrees(listOf(cloneTokenTree(TokenTree.Punct(this))))
    }

/** Adapts a [Group] as a [ToTokens] emitter. */
public fun Group.asToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        tokens.extendTokenTrees(listOf(cloneTokenTree(TokenTree.Group(this))))
    }

private object EmptyToTokens : ToTokens {
    override fun toTokens(tokens: TokenStream) {
        // Optional absent receiver emits zero tokens.
    }
}

/**
 * Treats a nullable [ToTokens] as a non-nullable emitter that produces
 * zero tokens when `null`.
 */
public fun ToTokens?.orEmpty(): ToTokens = this ?: EmptyToTokens

/**
 * Adapts a [ByteArray] holding C string bytes (without the trailing NUL) as
 * a [ToTokens] emitter.
 */
public fun ByteArray.asToTokensCString(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.cString(this).asToTokens().toTokens(tokens)
    }

public fun UByte.asU8ToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.u8Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun UShort.asU16ToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.u16Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun UInt.asU32ToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.u32Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun ULong.asU64ToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.u64Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun ULong.asU128ToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.u128Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun Byte.asI8ToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.i8Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun Short.asI16ToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.i16Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun Int.asI32ToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.i32Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun Long.asI64ToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.i64Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun Long.asI128ToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.i128Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun Float.asF32ToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.f32Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun Double.asF64ToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.f64Suffixed(this).asToTokens().toTokens(tokens)
    }

public fun Boolean.asBoolToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        val word = if (this) "true" else "false"
        Ident.new(word, Span.callSite()).asToTokens().toTokens(tokens)
    }

public fun Char.asCharToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.character(this).asToTokens().toTokens(tokens)
    }

public fun String.asStringToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.string(this).asToTokens().toTokens(tokens)
    }

public fun ULong.asUsizeToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.usizeSuffixed(this).asToTokens().toTokens(tokens)
    }

public fun Long.asIsizeToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.isizeSuffixed(this).asToTokens().toTokens(tokens)
    }
