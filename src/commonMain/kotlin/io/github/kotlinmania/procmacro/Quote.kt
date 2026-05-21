// port-lint: source src/quote.rs
package io.github.kotlinmania.procmacro

/*
 * # Quasiquoter
 * Implementation internals of the quasiquoter provided by `quote!`.
 *
 * This quasiquoter uses macros 2.0 hygiene to reliably access items
 * from `proc_macro`, to build a `proc_macro::TokenStream`.
 */

// Error messages used by quote() and helpers. Held as top-level constants
// rather than inline string literals because the upstream Rust messages
// contain `$` glyphs that would trip Kotlin string interpolation; the
// constant form keeps the human-facing text readable while avoiding the
// `${'$'}` escape that would otherwise litter the throw sites.
private const val DOLLAR_FOLLOW_ERROR: String =
    "dollar sign must be followed by an ident or another dollar sign or a repetition group in quote!"
private const val TRAILING_DOLLAR_ERROR: String =
    "unexpected trailing dollar sign in quote!"
private const val REPETITION_GROUP_TRAILER_ERROR: String =
    "dollar-paren group must be followed by * in quote!"

/**
 * Marker type meaning "this binding has an iterator". Upstream uses
 * `pub struct HasIterator;` (a zero-sized unit struct) plus a four-way
 * `BitOr` truth table over `HasIterator | ThereIsNoIteratorInRepetition`
 * to lift the per-meta-var "had an iterator" check into the type system
 * so the macro-expansion code can assert at compile time that at least
 * one meta-variable contributed an iterator. Kotlin has no compile-time
 * macro expansion, so the same lift happens at runtime via
 * [RepetitionIteratorCheck.or].
 */
public object HasIterator : RepetitionIteratorCheck()

/** Marker type meaning "this binding does not have an iterator". */
public object ThereIsNoIteratorInRepetition : RepetitionIteratorCheck()

/**
 * Common parent of the two repetition iterator markers. Translates the
 * four upstream `BitOr` impls:
 *
 *  * `NoIter | NoIter = NoIter`
 *  * `HasIter | NoIter = HasIter`
 *  * `NoIter | HasIter = HasIter`
 *  * `HasIter | HasIter = HasIter`
 *
 * into a single [or] method whose result is [HasIterator] if either side
 * is [HasIterator], else [ThereIsNoIteratorInRepetition].
 */
public sealed class RepetitionIteratorCheck {
    public infix fun or(other: RepetitionIteratorCheck): RepetitionIteratorCheck =
        if (this is HasIterator || other is HasIterator) HasIterator
        else ThereIsNoIteratorInRepetition
}

/*
 * Extension interfaces used by the implementation of `quote!`. These are
 * defined in separate interfaces, rather than as a single interface due
 * to ambiguity issues.
 *
 * These interfaces expose a `quoteIntoIter` method which should allow
 * calling whichever impl happens to be applicable. Calling that method
 * repeatedly on the returned value should be idempotent.
 *
 * Note: Kotlin extension resolution is static-only, so the Rust
 * ambiguity-resolution machinery (multiple traits implementable on the
 * same generic `T`) does not apply directly. The three interfaces below
 * exist so the upstream public surface is preserved; consumers can
 * dispatch via the per-receiver extension functions provided alongside
 * each interface.
 */

/**
 * Extension interface providing the `quoteIntoIter` method on
 * [Iterator]s. Mirrors upstream `RepIteratorExt`.
 */
public interface RepIteratorExt<T> {
    public fun quoteIntoIter(): Pair<Iterator<T>, HasIterator>
}

/**
 * Wrap a Kotlin [Iterator] as a [RepIteratorExt]. Mirrors
 * `impl<T: Iterator> RepIteratorExt for T`.
 */
public fun <T> Iterator<T>.quoteIntoIter(): Pair<Iterator<T>, HasIterator> =
    this to HasIterator

/**
 * Extension interface providing the `quoteIntoIter` method for
 * non-iterable types. Mirrors upstream `RepToTokensExt`. Non-iterable
 * types interpolate the same value in each iteration of the repetition.
 */
public interface RepToTokensExt {
    /**
     * Pretend to be an iterator for the purposes of [quoteIntoIter].
     * This allows repeated calls to [quoteIntoIter] to continue
     * correctly returning [ThereIsNoIteratorInRepetition].
     */
    public fun next(): RepToTokensExt? = this

    public fun quoteIntoIter(): Pair<RepToTokensExt, ThereIsNoIteratorInRepetition> =
        this to ThereIsNoIteratorInRepetition
}

/**
 * Extension interface providing the `quoteIntoIter` method for types
 * that can be referenced as an iterator. Mirrors upstream
 * `RepAsIteratorExt<'q>`. The `'q` lifetime collapses in Kotlin because
 * Kotlin references are not lifetime-tracked.
 */
public interface RepAsIteratorExt<T> {
    public fun quoteIntoIter(): Pair<Iterator<T>, HasIterator>
}

/** Mirrors `impl<'q, T: 'q> RepAsIteratorExt<'q> for [T]` and `for [T; N]`. */
public fun <T> Array<T>.quoteIntoIter(): Pair<Iterator<T>, HasIterator> =
    iterator() to HasIterator

/** Mirrors `impl<'q, T: 'q> RepAsIteratorExt<'q> for Vec<T>`. */
public fun <T> List<T>.quoteIntoIter(): Pair<Iterator<T>, HasIterator> =
    iterator() to HasIterator

/**
 * Mirrors `impl<'q, T: 'q> RepAsIteratorExt<'q> for BTreeSet<T>`. Kotlin
 * has no direct `BTreeSet` analog; the closest stdlib type is [Set],
 * whose iteration order is implementation-dependent. Callers who need
 * stable ordering should pre-sort before calling this extension.
 */
public fun <T> Set<T>.quoteIntoIter(): Pair<Iterator<T>, HasIterator> =
    iterator() to HasIterator

/**
 * Helper type used within interpolations to allow for repeated binding
 * names. Mirrors upstream `RepInterp<T>(pub T)`.
 *
 * Implements [Iterator] when [value] is an [Iterator], and exposes a
 * dummy [next] method used when a name is bound multiple times: the
 * previous binding shadows the original [Iterator] object so the macro
 * can avoid advancing the iterator multiple times per iteration.
 */
public class RepInterp<T>(public val value: T) {
    /**
     * Intended to look like `Iterator::next`. Called when a name is
     * bound multiple times; the previous binding shadows the original
     * [Iterator] object.
     */
    public fun nextShadow(): T? = value
}

/**
 * Mirrors `impl<T: Iterator> Iterator for RepInterp<T>`. Forwards
 * iteration to the wrapped [Iterator].
 */
public fun <T> RepInterp<Iterator<T>>.iteratorAdapter(): Iterator<T> = value

/** Mirrors `impl<T: ToTokens> ToTokens for RepInterp<T>`. */
public fun <T> RepInterp<T>.asToTokens(inner: (T) -> ToTokens): ToTokens =
    inner(value)

/**
 * Mirrors `impl<'q, T: RepAsIteratorExt<'q>> RepAsIteratorExt<'q> for RepInterp<T>`.
 */
public fun <T> RepInterp<List<T>>.quoteIntoIter(): Pair<Iterator<T>, HasIterator> =
    value.iterator() to HasIterator

/*
 * Upstream `minimal_quote_tt!`, `minimal_quote_ts!`, and `minimal_quote!`
 * are `macro_rules!` macros that generate code at compile time. They
 * bootstrap `quote()` itself because the real `quote!` cannot be used
 * inside the crate that defines it. Kotlin has no compile-time macro
 * expansion, so the equivalent helpers below are runtime functions that
 * build the same [TokenStream]s the macro expansions would produce.
 *
 * The helpers cover only the subset of tokens upstream uses to build
 * the body of [quote]: punctuation, identifiers, multi-character
 * operators (`::`, `=>`, `+=`, `!=`), and grouped sub-streams.
 */

private fun mkPunct(ch: Char): Punct = Punct.new(ch, Spacing.ALONE)

private fun mkIdentDef(name: String): Ident = Ident.new(name, Span.defSite())

private fun mkIdentCall(name: String): Ident = Ident.new(name, Span.callSite())

private fun mkGroup(delim: Delimiter, stream: TokenStream): Group =
    Group.new(delim, stream)

private fun streamOf(vararg trees: TokenTree): TokenStream =
    TokenStream.fromTokenTrees(trees.toList())

/**
 * Build the two-character operator token stream `::` / `=>` / `+=` /
 * `!=`. Upstream applies `Span::def_site()` to each piece before
 * concatenating; mirrored here.
 */
private fun mkJointOp(first: Char, second: Char): TokenStream {
    val a = TokenTree.Punct(Punct.new(first, Spacing.JOINT))
    val b = TokenTree.Punct(Punct.new(second, Spacing.ALONE))
    a.setSpan(Span.defSite())
    b.setSpan(Span.defSite())
    return TokenStream.fromTokenTrees(listOf(a, b))
}

private fun mkColonColon(): TokenStream = mkJointOp(':', ':')

private fun mkFatArrow(): TokenStream = mkJointOp('=', '>')

private fun mkPlusEq(): TokenStream = mkJointOp('+', '=')

private fun mkNotEq(): TokenStream = mkJointOp('!', '=')

/**
 * Recursively collects all [Ident]s (meta-variables) that follow a `$`
 * from the given content stream, preserving their order of appearance.
 *
 * Mirrors upstream `fn collect_meta_vars(content_stream) -> Vec<Ident>`.
 */
private fun collectMetaVars(contentStream: TokenStream): List<Ident> {
    val vars = mutableListOf<Ident>()
    collectMetaVarsInto(contentStream, vars)
    return vars
}

private fun collectMetaVarsInto(stream: TokenStream, out: MutableList<Ident>) {
    val iter = PeekableTokenTreeIterator(stream.iterator())
    while (iter.hasNext()) {
        val tree = iter.next()
        when {
            tree is TokenTree.Punct && tree.value.asChar() == '$' -> {
                val peeked = iter.peek()
                if (peeked is TokenTree.Ident) {
                    out.add(peeked.value)
                    iter.next()
                }
            }
            tree is TokenTree.Group -> collectMetaVarsInto(tree.value.stream(), out)
            else -> { /* not a meta-var marker */ }
        }
    }
}

/**
 * Peekable wrapper around a [TokenTree] iterator. Mirrors the upstream
 * `iter.peekable()` usage in [quote] and [collectMetaVars]; Kotlin's
 * stdlib does not expose a peekable iterator out of the box.
 */
private class PeekableTokenTreeIterator(private val inner: Iterator<TokenTree>) {
    private var lookahead: TokenTree? = null

    fun hasNext(): Boolean = lookahead != null || inner.hasNext()

    fun next(): TokenTree {
        val cached = lookahead
        if (cached != null) {
            lookahead = null
            return cached
        }
        return inner.next()
    }

    fun peek(): TokenTree? {
        if (lookahead == null && inner.hasNext()) {
            lookahead = inner.next()
        }
        return lookahead
    }
}

/**
 * Quote a [Span] into a [TokenStream]. Used to embed span identifiers
 * inside generated proc-macro output so the recovered tokens carry the
 * original hygiene. Mirrors upstream `pub fn quote_span`.
 */
public fun quoteSpan(procMacroCrate: TokenStream, span: Span): TokenStream {
    val id = span.saveSpan()
    // minimal_quote!((@ proc_macro_crate ) ::Span::recover_proc_macro_span((@ TokenTree::from(Literal::usize_unsuffixed(id)))))
    val out = TokenStream.new()
    out.extendTokenStreams(listOf(procMacroCrate))
    out.extendTokenStreams(listOf(mkColonColon()))
    out.extendTokenTrees(listOf(TokenTree.Ident(mkIdentCall("Span"))))
    out.extendTokenStreams(listOf(mkColonColon()))
    out.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentCall("recover_proc_macro_span")),
            TokenTree.Group(
                mkGroup(
                    Delimiter.PARENTHESIS,
                    streamOf(TokenTree.Literal(Literal.usizeUnsuffixed(id.toULong()))),
                ),
            ),
        ),
    )
    return out
}

/**
 * Quote a [TokenStream] into a [TokenStream]. This is the actual
 * implementation of the `quote!` proc macro: given an input stream of
 * tokens, produce a stream whose evaluation will reconstruct the input.
 *
 * Upstream registers this with the compiler in `register_builtin_macros`.
 *
 * Phase-1 caveats vs upstream Rust:
 *
 *  * Span fidelity depends on the in-process [SpanRegistry] backing
 *    [Span.saveSpan] and [Span.recoverProcMacroSpan] (the `bridge::client`
 *    cross-process span server is not ported).
 *  * The literal-roundtrip path emits the literal's source text via
 *    [Literal.toString] and reconstructs the literal with the
 *    `proc_macro::TokenStream::from_str` call shape upstream uses, so
 *    end-to-end reparse depends on [TokenStream.fromString] which is
 *    itself phase-1 stubbed. Until that is wired, the produced tokens
 *    are structurally correct but a downstream rustc-equivalent
 *    reparse step would be required.
 */
public fun quote(stream: TokenStream): TokenStream {
    if (stream.isEmpty()) {
        // minimal_quote!(crate::TokenStream::new())
        val emptyOut = TokenStream.new()
        emptyOut.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("crate"))))
        emptyOut.extendTokenStreams(listOf(mkColonColon()))
        emptyOut.extendTokenTrees(
            listOf(
                TokenTree.Ident(mkIdentDef("TokenStream")),
            ),
        )
        emptyOut.extendTokenStreams(listOf(mkColonColon()))
        emptyOut.extendTokenTrees(
            listOf(
                TokenTree.Ident(mkIdentDef("new")),
                TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, TokenStream.new())),
            ),
        )
        return emptyOut
    }

    val procMacroCrate = streamOf(TokenTree.Ident(mkIdentDef("crate")))
    var afterDollar = false

    val tokens = TokenStream.new()
    val iter = PeekableTokenTreeIterator(stream.iterator())
    while (iter.hasNext()) {
        val tree = iter.next()
        if (afterDollar) {
            afterDollar = false
            when (tree) {
                is TokenTree.Group -> {
                    val contents = tree.value.stream()
                    val sepOpt: Punct? = readRepetitionSeparator(iter)
                    val repExpanded = buildRepetitionExpansion(contents, sepOpt)
                    val groupedRep = TokenStream.new()
                    groupedRep.extendTokenTrees(
                        listOf(TokenTree.Group(mkGroup(Delimiter.BRACE, repExpanded))),
                    )
                    tokens.extendTokenStreams(listOf(groupedRep))
                    continue
                }

                is TokenTree.Ident -> {
                    // minimal_quote!(crate::ToTokens::to_tokens(&(@ tree), &mut ts);)
                    tokens.extendTokenStreams(
                        listOf(emitToTokensCall(streamOf(tree))),
                    )
                    continue
                }

                is TokenTree.Punct -> if (tree.value.asChar() == '$') {
                    // A doubled dollar literal escapes back to a real dollar token.
                } else {
                    throw IllegalArgumentException(DOLLAR_FOLLOW_ERROR)
                }

                else -> throw IllegalArgumentException(DOLLAR_FOLLOW_ERROR)
            }
        } else if (tree is TokenTree.Punct && tree.value.asChar() == '$') {
            afterDollar = true
            continue
        }

        val emitted: TokenStream = when (tree) {
            is TokenTree.Punct -> emitPunct(tree.value)
            is TokenTree.Group -> emitGroup(tree.value)
            is TokenTree.Ident -> emitIdent(tree.value, procMacroCrate)
            is TokenTree.Literal -> emitLiteral(tree.value, procMacroCrate)
        }
        tokens.extendTokenStreams(listOf(emitted))
    }
    if (afterDollar) {
        throw IllegalArgumentException(TRAILING_DOLLAR_ERROR)
    }

    // minimal_quote! {
    //   {
    //     let mut ts = crate::TokenStream::new();
    //     (@ tokens)
    //     ts
    //   }
    // }
    val body = TokenStream.new()
    body.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("let")),
            TokenTree.Ident(mkIdentDef("mut")),
            TokenTree.Ident(mkIdentDef("ts")),
            TokenTree.Punct(mkPunct('=')),
            TokenTree.Ident(mkIdentDef("crate")),
        ),
    )
    body.extendTokenStreams(listOf(mkColonColon()))
    body.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("TokenStream"))))
    body.extendTokenStreams(listOf(mkColonColon()))
    body.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("new")),
            TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, TokenStream.new())),
            TokenTree.Punct(mkPunct(';')),
        ),
    )
    body.extendTokenStreams(listOf(tokens))
    body.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("ts"))))

    val out = TokenStream.new()
    out.extendTokenTrees(listOf(TokenTree.Group(mkGroup(Delimiter.BRACE, body))))
    return out
}

/**
 * Read the optional separator + mandatory `*` after a `$(...)` repetition
 * group, mirroring the upstream `match (iter.next(), iter.peek())` block.
 * Returns the separator [Punct] if one was present, `null` if `*` came
 * directly after `)`.
 */
private fun readRepetitionSeparator(iter: PeekableTokenTreeIterator): Punct? {
    val first = if (iter.hasNext()) iter.next() else throw IllegalArgumentException(
        REPETITION_GROUP_TRAILER_ERROR,
    )
    val second = iter.peek()
    return when {
        first is TokenTree.Punct && second is TokenTree.Punct &&
            first.value.spacing() == Spacing.JOINT && second.value.asChar() == '*' -> {
            iter.next()
            first.value
        }
        first is TokenTree.Punct && first.value.asChar() == '*' -> null
        else -> throw IllegalArgumentException(
            REPETITION_GROUP_TRAILER_ERROR,
        )
    }
}

/**
 * Build the body of the expanded `$(...)SEP*` block: setup code that
 * pulls iterators out of the meta-variables, then the `while true { ... }`
 * loop that interpolates each iteration and emits the separator between
 * them.
 */
private fun buildRepetitionExpansion(contents: TokenStream, sepOpt: Punct?): TokenStream {
    val repExpanded = TokenStream.new()
    val metaVars = collectMetaVars(contents)

    // use crate::ext::*;
    repExpanded.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("use")),
            TokenTree.Ident(mkIdentDef("crate")),
        ),
    )
    repExpanded.extendTokenStreams(listOf(mkColonColon()))
    repExpanded.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("ext"))))
    repExpanded.extendTokenStreams(listOf(mkColonColon()))
    repExpanded.extendTokenTrees(
        listOf(
            TokenTree.Punct(mkPunct('*')),
            TokenTree.Punct(mkPunct(';')),
        ),
    )

    if (sepOpt != null) {
        // let mut _i = 0usize;
        repExpanded.extendTokenTrees(
            listOf(
                TokenTree.Ident(mkIdentDef("let")),
                TokenTree.Ident(mkIdentDef("mut")),
                TokenTree.Ident(mkIdentDef("_i")),
                TokenTree.Punct(mkPunct('=')),
                TokenTree.Literal(Literal.usizeSuffixed(0u.toULong())),
                TokenTree.Punct(mkPunct(';')),
            ),
        )
    } else {
        // ();
        repExpanded.extendTokenTrees(
            listOf(
                TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, TokenStream.new())),
                TokenTree.Punct(mkPunct(';')),
            ),
        )
    }

    // let has_iter = crate::ThereIsNoIteratorInRepetition;
    repExpanded.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("let")),
            TokenTree.Ident(mkIdentDef("has_iter")),
            TokenTree.Punct(mkPunct('=')),
            TokenTree.Ident(mkIdentDef("crate")),
        ),
    )
    repExpanded.extendTokenStreams(listOf(mkColonColon()))
    repExpanded.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("ThereIsNoIteratorInRepetition")),
            TokenTree.Punct(mkPunct(';')),
        ),
    )

    for (metaVar in metaVars) {
        // let (mut <m>, i) = <m>.quote_into_iter();
        // let has_iter = has_iter | i;
        repExpanded.extendTokenTrees(
            listOf(
                TokenTree.Ident(mkIdentDef("let")),
                TokenTree.Group(
                    mkGroup(
                        Delimiter.PARENTHESIS,
                        TokenStream.fromTokenTrees(
                            listOf(
                                TokenTree.Ident(mkIdentDef("mut")),
                                TokenTree.Ident(metaVar),
                                TokenTree.Punct(mkPunct(',')),
                                TokenTree.Ident(mkIdentDef("i")),
                            ),
                        ),
                    ),
                ),
                TokenTree.Punct(mkPunct('=')),
                TokenTree.Ident(metaVar),
                TokenTree.Punct(mkPunct('.')),
                TokenTree.Ident(mkIdentDef("quote_into_iter")),
                TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, TokenStream.new())),
                TokenTree.Punct(mkPunct(';')),
                TokenTree.Ident(mkIdentDef("let")),
                TokenTree.Ident(mkIdentDef("has_iter")),
                TokenTree.Punct(mkPunct('=')),
                TokenTree.Ident(mkIdentDef("has_iter")),
                TokenTree.Punct(mkPunct('|')),
                TokenTree.Ident(mkIdentDef("i")),
                TokenTree.Punct(mkPunct(';')),
            ),
        )
    }

    // let _: crate::HasIterator = has_iter;
    repExpanded.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("let")),
            TokenTree.Ident(mkIdentDef("_")),
            TokenTree.Punct(mkPunct(':')),
            TokenTree.Ident(mkIdentDef("crate")),
        ),
    )
    repExpanded.extendTokenStreams(listOf(mkColonColon()))
    repExpanded.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("HasIterator")),
            TokenTree.Punct(mkPunct('=')),
            TokenTree.Ident(mkIdentDef("has_iter")),
            TokenTree.Punct(mkPunct(';')),
        ),
    )

    // while true { <body> }
    val whileBody = buildRepetitionWhileBody(metaVars, contents, sepOpt)
    repExpanded.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentCall("while")),
            TokenTree.Ident(mkIdentCall("true")),
            TokenTree.Group(mkGroup(Delimiter.BRACE, whileBody)),
        ),
    )

    return repExpanded
}

private fun buildRepetitionWhileBody(
    metaVars: List<Ident>,
    contents: TokenStream,
    sepOpt: Punct?,
): TokenStream {
    val whileBody = TokenStream.new()
    for (metaVar in metaVars) {
        // let <m> = match <m>.next() {
        //   Some(_x) => crate::RepInterp(_x),
        //   None => break,
        // };
        whileBody.extendTokenTrees(
            listOf(
                TokenTree.Ident(mkIdentDef("let")),
                TokenTree.Ident(metaVar),
                TokenTree.Punct(mkPunct('=')),
                TokenTree.Ident(mkIdentDef("match")),
                TokenTree.Ident(metaVar),
                TokenTree.Punct(mkPunct('.')),
                TokenTree.Ident(mkIdentDef("next")),
                TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, TokenStream.new())),
                TokenTree.Group(
                    mkGroup(
                        Delimiter.BRACE,
                        buildMatchArmsForMetaVarPull(),
                    ),
                ),
                TokenTree.Punct(mkPunct(';')),
            ),
        )
    }

    if (sepOpt != null) {
        // if _i > 0 { crate::ToTokens::to_tokens(&crate::TokenTree::Punct(crate::Punct::new('<sep>', crate::Spacing::Alone)), &mut ts); }
        // _i += 1;
        val sepCharStream = streamOf(TokenTree.Literal(Literal.character(sepOpt.asChar())))
        val sepEmitCall = emitPunctReconstruction(sepCharStream, spacingAloneRef())
        whileBody.extendTokenTrees(
            listOf(
                TokenTree.Ident(mkIdentDef("if")),
                TokenTree.Ident(mkIdentDef("_i")),
                TokenTree.Punct(mkPunct('>')),
                TokenTree.Literal(Literal.usizeSuffixed(0u.toULong())),
            ),
        )
        whileBody.extendTokenTrees(
            listOf(
                TokenTree.Group(mkGroup(Delimiter.BRACE, sepEmitCall)),
            ),
        )
        whileBody.extendTokenTrees(
            listOf(
                TokenTree.Ident(mkIdentDef("_i")),
            ),
        )
        whileBody.extendTokenStreams(listOf(mkPlusEq()))
        whileBody.extendTokenTrees(
            listOf(
                TokenTree.Literal(Literal.usizeSuffixed(1u.toULong())),
                TokenTree.Punct(mkPunct(';')),
            ),
        )
    } else {
        // ();
        whileBody.extendTokenTrees(
            listOf(
                TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, TokenStream.new())),
                TokenTree.Punct(mkPunct(';')),
            ),
        )
    }

    // quote(contents.clone()).to_tokens(&mut ts);
    val innerQuote = quote(contents)
    whileBody.extendTokenStreams(listOf(innerQuote))
    whileBody.extendTokenTrees(
        listOf(
            TokenTree.Punct(mkPunct('.')),
            TokenTree.Ident(mkIdentDef("to_tokens")),
            TokenTree.Group(
                mkGroup(
                    Delimiter.PARENTHESIS,
                    TokenStream.fromTokenTrees(
                        listOf(
                            TokenTree.Punct(mkPunct('&')),
                            TokenTree.Ident(mkIdentDef("mut")),
                            TokenTree.Ident(mkIdentDef("ts")),
                        ),
                    ),
                ),
            ),
            TokenTree.Punct(mkPunct(';')),
        ),
    )

    return whileBody
}

private fun buildMatchArmsForMetaVarPull(): TokenStream {
    // Some(_x) => crate::RepInterp(_x),
    // None => break,
    val arms = TokenStream.new()
    arms.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("Some")),
            TokenTree.Group(
                mkGroup(
                    Delimiter.PARENTHESIS,
                    streamOf(TokenTree.Ident(mkIdentDef("_x"))),
                ),
            ),
        ),
    )
    arms.extendTokenStreams(listOf(mkFatArrow()))
    arms.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("crate")),
        ),
    )
    arms.extendTokenStreams(listOf(mkColonColon()))
    arms.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("RepInterp")),
            TokenTree.Group(
                mkGroup(
                    Delimiter.PARENTHESIS,
                    streamOf(TokenTree.Ident(mkIdentDef("_x"))),
                ),
            ),
            TokenTree.Punct(mkPunct(',')),
            TokenTree.Ident(mkIdentDef("None")),
        ),
    )
    arms.extendTokenStreams(listOf(mkFatArrow()))
    arms.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("break")),
            TokenTree.Punct(mkPunct(',')),
        ),
    )
    return arms
}

private fun spacingAloneRef(): TokenStream {
    // crate::Spacing::Alone
    val out = TokenStream.new()
    out.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("crate"))))
    out.extendTokenStreams(listOf(mkColonColon()))
    out.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("Spacing"))))
    out.extendTokenStreams(listOf(mkColonColon()))
    out.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("Alone"))))
    return out
}

private fun spacingJointRef(): TokenStream {
    val out = TokenStream.new()
    out.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("crate"))))
    out.extendTokenStreams(listOf(mkColonColon()))
    out.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("Spacing"))))
    out.extendTokenStreams(listOf(mkColonColon()))
    out.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("Joint"))))
    return out
}

private fun emitToTokensCall(argStream: TokenStream): TokenStream {
    // crate::ToTokens::to_tokens(& <arg>, &mut ts);
    val out = TokenStream.new()
    out.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("crate"))))
    out.extendTokenStreams(listOf(mkColonColon()))
    out.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("ToTokens"))))
    out.extendTokenStreams(listOf(mkColonColon()))
    out.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("to_tokens")),
        ),
    )

    val args = TokenStream.new()
    args.extendTokenTrees(listOf(TokenTree.Punct(mkPunct('&'))))
    args.extendTokenStreams(listOf(argStream))
    args.extendTokenTrees(
        listOf(
            TokenTree.Punct(mkPunct(',')),
            TokenTree.Punct(mkPunct('&')),
            TokenTree.Ident(mkIdentDef("mut")),
            TokenTree.Ident(mkIdentDef("ts")),
        ),
    )

    out.extendTokenTrees(
        listOf(
            TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, args)),
            TokenTree.Punct(mkPunct(';')),
        ),
    )
    return out
}

private fun emitPunctReconstruction(charLiteralStream: TokenStream, spacingStream: TokenStream): TokenStream {
    // crate::ToTokens::to_tokens(&crate::TokenTree::Punct(crate::Punct::new(<char>, <spacing>)), &mut ts);
    val inner = TokenStream.new()
    inner.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("crate"))))
    inner.extendTokenStreams(listOf(mkColonColon()))
    inner.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("TokenTree"))))
    inner.extendTokenStreams(listOf(mkColonColon()))
    inner.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("Punct")),
        ),
    )

    val punctArgs = TokenStream.new()
    punctArgs.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("crate"))))
    punctArgs.extendTokenStreams(listOf(mkColonColon()))
    punctArgs.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("Punct"))))
    punctArgs.extendTokenStreams(listOf(mkColonColon()))
    punctArgs.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("new")),
        ),
    )
    val newArgs = TokenStream.new()
    newArgs.extendTokenStreams(listOf(charLiteralStream))
    newArgs.extendTokenTrees(listOf(TokenTree.Punct(mkPunct(','))))
    newArgs.extendTokenStreams(listOf(spacingStream))
    punctArgs.extendTokenTrees(
        listOf(TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, newArgs))),
    )

    inner.extendTokenTrees(
        listOf(TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, punctArgs))),
    )
    return emitToTokensCall(inner)
}

private fun emitPunct(p: Punct): TokenStream {
    val spacingStream = when (p.spacing()) {
        Spacing.ALONE -> spacingAloneRef()
        Spacing.JOINT -> spacingJointRef()
    }
    val charStream = streamOf(TokenTree.Literal(Literal.character(p.asChar())))
    return emitPunctReconstruction(charStream, spacingStream)
}

private fun emitGroup(g: Group): TokenStream {
    // crate::ToTokens::to_tokens(&crate::TokenTree::Group(crate::Group::new(<delim>, <quote(stream)>)), &mut ts);
    val delimRef = TokenStream.new()
    delimRef.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("crate"))))
    delimRef.extendTokenStreams(listOf(mkColonColon()))
    delimRef.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("Delimiter"))))
    delimRef.extendTokenStreams(listOf(mkColonColon()))
    delimRef.extendTokenTrees(
        listOf(
            TokenTree.Ident(
                mkIdentDef(
                    when (g.delimiter()) {
                        Delimiter.PARENTHESIS -> "Parenthesis"
                        Delimiter.BRACE -> "Brace"
                        Delimiter.BRACKET -> "Bracket"
                        Delimiter.NONE -> "None"
                    },
                ),
            ),
        ),
    )

    val nestedQuote = quote(g.stream())

    val newArgs = TokenStream.new()
    newArgs.extendTokenStreams(listOf(delimRef))
    newArgs.extendTokenTrees(listOf(TokenTree.Punct(mkPunct(','))))
    newArgs.extendTokenStreams(listOf(nestedQuote))

    val groupCtor = TokenStream.new()
    groupCtor.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("crate"))))
    groupCtor.extendTokenStreams(listOf(mkColonColon()))
    groupCtor.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("Group"))))
    groupCtor.extendTokenStreams(listOf(mkColonColon()))
    groupCtor.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("new")),
            TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, newArgs)),
        ),
    )

    val ttGroup = TokenStream.new()
    ttGroup.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("crate"))))
    ttGroup.extendTokenStreams(listOf(mkColonColon()))
    ttGroup.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("TokenTree"))))
    ttGroup.extendTokenStreams(listOf(mkColonColon()))
    ttGroup.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("Group")),
            TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, groupCtor)),
        ),
    )

    return emitToTokensCall(ttGroup)
}

private fun emitIdent(ident: Ident, procMacroCrate: TokenStream): TokenStream {
    val literal = ident.toString()
    val rawPrefix = "r#"
    val (textForLiteral, ctorName) = if (literal.startsWith(rawPrefix)) {
        literal.removePrefix(rawPrefix) to "new_raw"
    } else {
        literal to "new"
    }

    val identCtorPath = TokenStream.new()
    identCtorPath.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("crate"))))
    identCtorPath.extendTokenStreams(listOf(mkColonColon()))
    identCtorPath.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("Ident"))))
    identCtorPath.extendTokenStreams(listOf(mkColonColon()))
    identCtorPath.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef(ctorName))))

    val newArgs = TokenStream.new()
    newArgs.extendTokenTrees(listOf(TokenTree.Literal(Literal.string(textForLiteral))))
    newArgs.extendTokenTrees(listOf(TokenTree.Punct(mkPunct(','))))
    newArgs.extendTokenStreams(listOf(quoteSpan(procMacroCrate, ident.span())))

    val identCtorCall = TokenStream.new()
    identCtorCall.extendTokenStreams(listOf(identCtorPath))
    identCtorCall.extendTokenTrees(
        listOf(TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, newArgs))),
    )

    val ttIdent = TokenStream.new()
    ttIdent.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("crate"))))
    ttIdent.extendTokenStreams(listOf(mkColonColon()))
    ttIdent.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("TokenTree"))))
    ttIdent.extendTokenStreams(listOf(mkColonColon()))
    ttIdent.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("Ident")),
            TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, identCtorCall)),
        ),
    )

    return emitToTokensCall(ttIdent)
}

private fun emitLiteral(literal: Literal, procMacroCrate: TokenStream): TokenStream {
    // Emit:
    // crate::ToTokens::to_tokens(&crate::TokenTree::Literal({
    //     let mut iter = <text>.parse::<crate::TokenStream>().unwrap().into_iter();
    //     if let (Some(crate::TokenTree::Literal(mut lit)), None) = (iter.next(), iter.next()) {
    //         lit.set_span(<span>);
    //         lit
    //     } else { unreachable!() }
    // }), &mut ts);
    //
    // The closing reparse depends on `proc_macro::TokenStream::from_str`,
    // which lives in TokenStream.fromString and is phase-1 stubbed. The
    // shape of the emitted code is preserved verbatim so a downstream
    // runtime that wires the FromStr impl picks it up unchanged.
    val literalTextStream = streamOf(
        TokenTree.Literal(Literal.string(literal.toString())),
    )

    val parseCallTrees = mutableListOf<TokenTree>(
        TokenTree.Punct(mkPunct('.')),
        TokenTree.Ident(mkIdentDef("parse")),
        TokenTree.Punct(Punct.new(':', Spacing.JOINT)),
        TokenTree.Punct(Punct.new(':', Spacing.ALONE)),
        TokenTree.Punct(mkPunct('<')),
        TokenTree.Ident(mkIdentDef("crate")),
    )
    val parseCallFront = TokenStream.fromTokenTrees(parseCallTrees)
    val parseCallTail = TokenStream.new()
    parseCallTail.extendTokenStreams(listOf(mkColonColon()))
    parseCallTail.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("TokenStream")),
            TokenTree.Punct(mkPunct('>')),
            TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, TokenStream.new())),
            TokenTree.Punct(mkPunct('.')),
            TokenTree.Ident(mkIdentDef("unwrap")),
            TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, TokenStream.new())),
            TokenTree.Punct(mkPunct('.')),
            TokenTree.Ident(mkIdentDef("into_iter")),
            TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, TokenStream.new())),
        ),
    )

    val letIterRhs = TokenStream.new()
    letIterRhs.extendTokenStreams(listOf(literalTextStream))
    letIterRhs.extendTokenStreams(listOf(parseCallFront))
    letIterRhs.extendTokenStreams(listOf(parseCallTail))

    val body = TokenStream.new()
    body.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("let")),
            TokenTree.Ident(mkIdentDef("mut")),
            TokenTree.Ident(mkIdentDef("iter")),
            TokenTree.Punct(mkPunct('=')),
        ),
    )
    body.extendTokenStreams(listOf(letIterRhs))
    body.extendTokenTrees(listOf(TokenTree.Punct(mkPunct(';'))))

    val tuplePattern = TokenStream.fromTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("Some")),
            TokenTree.Group(
                mkGroup(
                    Delimiter.PARENTHESIS,
                    TokenStream.fromTokenTrees(
                        listOf(
                            TokenTree.Ident(mkIdentDef("crate")),
                        ),
                    ).also {
                        it.extendTokenStreams(listOf(mkColonColon()))
                        it.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("TokenTree"))))
                        it.extendTokenStreams(listOf(mkColonColon()))
                        it.extendTokenTrees(
                            listOf(
                                TokenTree.Ident(mkIdentDef("Literal")),
                                TokenTree.Group(
                                    mkGroup(
                                        Delimiter.PARENTHESIS,
                                        TokenStream.fromTokenTrees(
                                            listOf(
                                                TokenTree.Ident(mkIdentDef("mut")),
                                                TokenTree.Ident(mkIdentDef("lit")),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        )
                    },
                ),
            ),
            TokenTree.Punct(mkPunct(',')),
            TokenTree.Ident(mkIdentDef("None")),
        ),
    )

    val callPair = TokenStream.fromTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("iter")),
            TokenTree.Punct(mkPunct('.')),
            TokenTree.Ident(mkIdentDef("next")),
            TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, TokenStream.new())),
            TokenTree.Punct(mkPunct(',')),
            TokenTree.Ident(mkIdentDef("iter")),
            TokenTree.Punct(mkPunct('.')),
            TokenTree.Ident(mkIdentDef("next")),
            TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, TokenStream.new())),
        ),
    )

    val thenBody = TokenStream.new()
    thenBody.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("lit")),
            TokenTree.Punct(mkPunct('.')),
            TokenTree.Ident(mkIdentDef("set_span")),
            TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, quoteSpan(procMacroCrate, literal.span()))),
            TokenTree.Punct(mkPunct(';')),
            TokenTree.Ident(mkIdentDef("lit")),
        ),
    )

    val elseBody = TokenStream.fromTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("unreachable")),
            TokenTree.Punct(Punct.new('!', Spacing.ALONE)),
            TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, TokenStream.new())),
        ),
    )

    body.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("if")),
            TokenTree.Ident(mkIdentDef("let")),
            TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, tuplePattern)),
            TokenTree.Punct(mkPunct('=')),
            TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, callPair)),
            TokenTree.Group(mkGroup(Delimiter.BRACE, thenBody)),
            TokenTree.Ident(mkIdentDef("else")),
            TokenTree.Group(mkGroup(Delimiter.BRACE, elseBody)),
        ),
    )

    val ttLiteral = TokenStream.new()
    ttLiteral.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("crate"))))
    ttLiteral.extendTokenStreams(listOf(mkColonColon()))
    ttLiteral.extendTokenTrees(listOf(TokenTree.Ident(mkIdentDef("TokenTree"))))
    ttLiteral.extendTokenStreams(listOf(mkColonColon()))
    ttLiteral.extendTokenTrees(
        listOf(
            TokenTree.Ident(mkIdentDef("Literal")),
            TokenTree.Group(mkGroup(Delimiter.BRACE, body)),
        ),
    )

    return emitToTokensCall(ttLiteral)
}
