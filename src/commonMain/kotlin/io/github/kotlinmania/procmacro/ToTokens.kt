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
private class FunctionalToTokens(
    private val emit: (TokenStream) -> Unit,
) : ToTokens {
    override fun toTokens(tokens: TokenStream): Unit = emit(tokens)
}

private class TokenTreeToTokens(
    private val tree: TokenTree,
) : ToTokens {
    override fun toTokens(tokens: TokenStream) {
        tokens.extendTokenTrees(listOf(tree))
    }

    override fun intoTokenStream(): TokenStream {
        // Upstream calls `ConcatTreesHelper::new(1).push(self).build()` to
        // construct a single-tree stream without an intermediate empty
        // builder. The produced [TokenStream] is observably equivalent to
        // the default [toTokenStream] route, so forwarding preserves behavior.
        return toTokenStream()
    }
}

/**
 * Adapt a [TokenTree] as a [ToTokens]. Mirrors the upstream
 * `impl ToTokens for TokenTree`.
 */
public fun TokenTree.asToTokens(): ToTokens = TokenTreeToTokens(this)

private class TokenStreamToTokens(
    private val stream: TokenStream,
) : ToTokens {
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
public fun Literal.asToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        tokens.extendTokenTrees(listOf(TokenTree.Literal(this)))
    }

/**
 * Adapt an [Ident] as a [ToTokens]. Mirrors the upstream
 * `impl ToTokens for Ident`.
 */
public fun Ident.asToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        tokens.extendTokenTrees(listOf(TokenTree.Ident(this)))
    }

/**
 * Adapt a [Punct] as a [ToTokens]. Mirrors the upstream
 * `impl ToTokens for Punct`.
 */
public fun Punct.asToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
        tokens.extendTokenTrees(listOf(TokenTree.Punct(this)))
    }

/**
 * Adapt a [Group] as a [ToTokens]. Mirrors the upstream
 * `impl ToTokens for Group`.
 */
public fun Group.asToTokens(): ToTokens =
    FunctionalToTokens { tokens ->
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
// caller already has the unwrapped value and invokes its own adapter
// directly. This comment is the translated stand-in so the
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

/**
 * Adapt a [ByteArray] holding C string bytes (without the trailing NUL) as
 * a [ToTokens]. Mirrors the upstream `impl ToTokens for CStr` and
 * `impl ToTokens for CString`, which collapse to a single Kotlin impl
 * because Kotlin has no dedicated C-string type. The factory is named
 * explicitly because [ByteArray] is also used for byte-string literals;
 * callers that want the `b"..."` shape build the literal via
 * [Literal.byteString] and then call [asToTokens] on the [Literal].
 */
public fun ByteArray.asToTokensCString(): ToTokens =
    FunctionalToTokens { tokens ->
        Literal.cString(this).asToTokens().toTokens(tokens)
    }
