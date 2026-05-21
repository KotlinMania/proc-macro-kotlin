// port-lint: source src/to_tokens.rs
package io.github.kotlinmania.procmacro

/**
 * Types that can be interpolated inside a `quote!` invocation.
 */
public interface ToTokens {
    /** Write `this` to the given [TokenStream]. */
    public fun toTokens(tokens: TokenStream)

    /**
     * Convert `this` directly into a [TokenStream] object.
     *
     * This method is implicitly implemented using [toTokens], and acts as a
     * convenience method for consumers of the [ToTokens] interface.
     */
    public fun toTokenStream(): TokenStream {
        val tokens = TokenStream.new()
        toTokens(tokens)
        return tokens
    }

    /**
     * Convert `this` directly into a [TokenStream] object.
     *
     * This method is implicitly implemented using [toTokens], and acts as a
     * convenience method for consumers of the [ToTokens] interface.
     */
    public fun intoTokenStream(): TokenStream = toTokenStream()
}

/**
 * Adapter for impls whose only override is `to_tokens`. Used by every
 * primitive and pass-through impl below; the two adapters that override
 * `into_token_stream` (TokenTree, TokenStream) define their own class.
 */
private class FunctionalToTokens(private val emit: (TokenStream) -> Unit) : ToTokens {
    override fun toTokens(tokens: TokenStream): Unit = emit(tokens)
}

private class TokenTreeToTokens(private val tree: TokenTree) : ToTokens {
    override fun toTokens(tokens: TokenStream) {
        tokens.extendTokenTrees(listOf(tree))
    }

    override fun intoTokenStream(): TokenStream {
        // Upstream calls `ConcatTreesHelper::new(1).push(self).build()` to
        // construct a single-tree stream without an intermediate empty
        // builder. `ConcatTreesHelper` lives in `tmp/proc-macro/src/lib.rs`
        // and is not yet ported here; the produced [TokenStream] is
        // observably equivalent to the default [toTokenStream] route, so
        // forwarding preserves behavior. When the helper is ported, replace
        // this body with the matching builder call.
        return toTokenStream()
    }
}

/**
 * Adapt a [TokenTree] as a [ToTokens]. Mirrors the upstream
 * `impl ToTokens for TokenTree`.
 */
public fun TokenTree.asToTokens(): ToTokens = TokenTreeToTokens(this)

private class TokenStreamToTokens(private val stream: TokenStream) : ToTokens {
    override fun toTokens(tokens: TokenStream) {
        tokens.extendTokenStreams(listOf(stream))
    }

    override fun intoTokenStream(): TokenStream = stream
}

/**
 * Adapt a [TokenStream] as a [ToTokens]. Mirrors the upstream
 * `impl ToTokens for TokenStream`.
 */
public fun TokenStream.asToTokens(): ToTokens = TokenStreamToTokens(this)

/**
 * Adapt a [Literal] as a [ToTokens]. Mirrors the upstream
 * `impl ToTokens for Literal`.
 */
public fun Literal.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    tokens.extendTokenTrees(listOf(TokenTree.Literal(this)))
}

/**
 * Adapt an [Ident] as a [ToTokens]. Mirrors the upstream
 * `impl ToTokens for Ident`.
 */
public fun Ident.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    tokens.extendTokenTrees(listOf(TokenTree.Ident(this)))
}

/**
 * Adapt a [Punct] as a [ToTokens]. Mirrors the upstream
 * `impl ToTokens for Punct`.
 */
public fun Punct.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    tokens.extendTokenTrees(listOf(TokenTree.Punct(this)))
}

/**
 * Adapt a [Group] as a [ToTokens]. Mirrors the upstream
 * `impl ToTokens for Group`.
 */
public fun Group.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    tokens.extendTokenTrees(listOf(TokenTree.Group(this)))
}

// Upstream defines five reference-forwarding impls:
//
//   impl<T: ToTokens + ?Sized> ToTokens for &T
//   impl<T: ToTokens + ?Sized> ToTokens for &mut T
//   impl<T: ToTokens + ?Sized> ToTokens for Box<T>
//   impl<T: ToTokens + ?Sized> ToTokens for Rc<T>
//   impl<T: ToTokens + ToOwned + ?Sized> ToTokens for Cow<'_, T>
//
// Each one forwards `(**self).to_tokens(tokens)` — the wrapper dereferences
// to the inner value and delegates. Kotlin has no `&T` / `&mut T` borrow
// distinction; `Box<T>`, `Rc<T>`, and `Cow<'_, T>` likewise have no native
// Kotlin counterparts (all references are shared by default and storage is
// the runtime's concern). The five impls therefore collapse: a Kotlin
// caller already has the unwrapped value and invokes its own
// [asToTokens] directly. This comment is the translated stand-in so the
// missing impls are visible in source order.

private object EmptyToTokens : ToTokens {
    override fun toTokens(tokens: TokenStream) {
        // Mirrors the upstream `impl ToTokens for Option<T>` `None` branch:
        // emit no tokens.
    }
}

/**
 * Treat a nullable [ToTokens] as a non-nullable one. Mirrors the upstream
 * `impl<T: ToTokens> ToTokens for Option<T>`: `Some(t)` forwards to `t`,
 * `None` emits no tokens.
 */
public fun ToTokens?.orEmpty(): ToTokens = this ?: EmptyToTokens

/** Adapt a [UByte] as a [ToTokens]. Mirrors the upstream `impl ToTokens for u8`. */
public fun UByte.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    Literal.u8Suffixed(this).asToTokens().toTokens(tokens)
}

/** Adapt a [UShort] as a [ToTokens]. Mirrors the upstream `impl ToTokens for u16`. */
public fun UShort.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    Literal.u16Suffixed(this).asToTokens().toTokens(tokens)
}

/** Adapt a [UInt] as a [ToTokens]. Mirrors the upstream `impl ToTokens for u32`. */
public fun UInt.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    Literal.u32Suffixed(this).asToTokens().toTokens(tokens)
}

/**
 * Adapt a [ULong] as a [ToTokens]. Mirrors the upstream
 * `impl ToTokens for u64`. Rust's `u128` and `usize` also use [ULong] as
 * their closest Kotlin counterpart; callers that need the `u128`/`usize`
 * suffix shape construct the literal directly via
 * [Literal.u128Suffixed] / [Literal.usizeSuffixed] and then call
 * [asToTokens] on the resulting [Literal].
 */
public fun ULong.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    Literal.u64Suffixed(this).asToTokens().toTokens(tokens)
}

/** Adapt a [Byte] as a [ToTokens]. Mirrors the upstream `impl ToTokens for i8`. */
public fun Byte.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    Literal.i8Suffixed(this).asToTokens().toTokens(tokens)
}

/** Adapt a [Short] as a [ToTokens]. Mirrors the upstream `impl ToTokens for i16`. */
public fun Short.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    Literal.i16Suffixed(this).asToTokens().toTokens(tokens)
}

/** Adapt an [Int] as a [ToTokens]. Mirrors the upstream `impl ToTokens for i32`. */
public fun Int.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    Literal.i32Suffixed(this).asToTokens().toTokens(tokens)
}

/**
 * Adapt a [Long] as a [ToTokens]. Mirrors the upstream
 * `impl ToTokens for i64`. Rust's `i128` and `isize` also use [Long] as
 * their closest Kotlin counterpart; callers that need the `i128`/`isize`
 * suffix shape construct the literal directly via
 * [Literal.i128Suffixed] / [Literal.isizeSuffixed] and then call
 * [asToTokens] on the resulting [Literal].
 */
public fun Long.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    Literal.i64Suffixed(this).asToTokens().toTokens(tokens)
}

/** Adapt a [Float] as a [ToTokens]. Mirrors the upstream `impl ToTokens for f32`. */
public fun Float.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    Literal.f32Suffixed(this).asToTokens().toTokens(tokens)
}

/** Adapt a [Double] as a [ToTokens]. Mirrors the upstream `impl ToTokens for f64`. */
public fun Double.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    Literal.f64Suffixed(this).asToTokens().toTokens(tokens)
}

/** Adapt a [Boolean] as a [ToTokens]. Mirrors the upstream `impl ToTokens for bool`. */
public fun Boolean.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    val word = if (this) "true" else "false"
    Ident.new(word, Span.callSite()).asToTokens().toTokens(tokens)
}

/** Adapt a [Char] as a [ToTokens]. Mirrors the upstream `impl ToTokens for char`. */
public fun Char.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    Literal.character(this).asToTokens().toTokens(tokens)
}

/**
 * Adapt a [String] as a [ToTokens]. Mirrors the upstream
 * `impl ToTokens for str` and `impl ToTokens for String`, which collapse
 * to a single Kotlin impl because [String] covers both the borrowed `&str`
 * and the owned `String` shapes.
 */
public fun String.asToTokens(): ToTokens = FunctionalToTokens { tokens ->
    Literal.string(this).asToTokens().toTokens(tokens)
}

/**
 * Adapt a [ByteArray] holding C string bytes (without the trailing NUL) as
 * a [ToTokens]. Mirrors the upstream `impl ToTokens for CStr` and
 * `impl ToTokens for CString`, which collapse to a single Kotlin impl
 * because Kotlin has no dedicated C-string type. The factory is named
 * explicitly because [ByteArray] is also used for byte-string literals;
 * callers that want the `b"..."` shape build the literal via
 * [Literal.byteString] and then call [asToTokens] on the [Literal].
 */
public fun ByteArray.asToTokensCString(): ToTokens = FunctionalToTokens { tokens ->
    Literal.cString(this).asToTokens().toTokens(tokens)
}
