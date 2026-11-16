// port-lint: source src/quote.rs
package io.github.kotlinmania.procmacro

/*
 * Implementation internals of the quasiquoter, building a [TokenStream]
 * whose evaluation reconstructs the quoted token stream with preserved
 * macro hygiene.
 */

private const val DOLLAR_FOLLOW_ERROR: String =
    "dollar sign must be followed by an ident or another dollar sign or a repetition group in quote!"
private const val TRAILING_DOLLAR_ERROR: String =
    "unexpected trailing dollar sign in quote!"
private const val REPETITION_GROUP_TRAILER_ERROR: String =
    "dollar-paren group must be followed by * in quote!"

/**
 * Marker type meaning that a repetition binding supplies an iterator.
 */
public object HasIterator : RepetitionIteratorCheck()

/** Marker type meaning that a repetition binding does not supply an iterator. */
public object ThereIsNoIteratorInRepetition : RepetitionIteratorCheck()

/**
 * Common parent of the two repetition iterator markers, combining two
 * markers via [or] to produce [HasIterator] whenever at least one operand
 * is [HasIterator].
 */
public sealed class RepetitionIteratorCheck {
    public infix fun or(other: RepetitionIteratorCheck): RepetitionIteratorCheck =
        if (this is HasIterator || other is HasIterator) {
            HasIterator
        } else {
            ThereIsNoIteratorInRepetition
        }
}

/**
 * Result of adapting a token-tree [Iterator] as a repetition source.
 */
internal class TokenTreeIteratorResult(
    val iterator: Iterator<TokenTree>,
    val marker: HasIterator,
) {
    operator fun component1(): Iterator<TokenTree> = iterator

    operator fun component2(): HasIterator = marker
}

/** Wraps a [TokenTree] [Iterator] as a repetition source. */
internal fun Iterator<TokenTree>.quoteIntoIter(): TokenTreeIteratorResult = TokenTreeIteratorResult(this, HasIterator)

/**
 * Extension interface providing [quoteIntoIter] for non-iterable values,
 * which interpolate the same value on every iteration of a repetition.
 */
internal interface RepToTokensExt {
    /**
     * Returns this receiver unchanged so repeated calls to [quoteIntoIter]
     * remain idempotent and keep returning [ThereIsNoIteratorInRepetition].
     */
    fun next(): RepToTokensExt? = this

    fun quoteIntoIter(): Pair<RepToTokensExt, ThereIsNoIteratorInRepetition> = this to ThereIsNoIteratorInRepetition
}

/** Converts a token-tree array into a repetition iterator. */
internal fun Array<TokenTree>.quoteIntoIter(): TokenTreeIteratorResult =
    TokenTreeIteratorResult(iterator(), HasIterator)

/** Converts a token-tree list into a repetition iterator. */
internal fun List<TokenTree>.quoteIntoIter(): TokenTreeIteratorResult = TokenTreeIteratorResult(iterator(), HasIterator)

/** Converts a token-tree set into a repetition iterator. */
internal fun Set<TokenTree>.quoteIntoIter(): TokenTreeIteratorResult = TokenTreeIteratorResult(iterator(), HasIterator)

/**
 * Helper wrapper used within repetition interpolations so repeated binding
 * names shadow the outer iterator and do not advance it multiple times per
 * iteration.
 */
internal class RepInterp(
    val value: TokenTree,
) {
    /** Returns the bound [TokenTree] for shadowed repetition bindings. */
    fun nextShadow(): TokenTree? = value
}

/**
 * Forwards iteration to the wrapped token-tree [Iterator].
 */
internal class RepInterpTokenTreeIterator(
    val value: Iterator<TokenTree>,
) {
    fun nextShadow(): Iterator<TokenTree>? = value
}

internal fun RepInterpTokenTreeIterator.iteratorAdapter(): Iterator<TokenTree> = value

/** Converts a repeated token value to tokens. */
internal class RepInterpToTokens(
    val value: ToTokens,
) {
    fun nextShadow(): ToTokens? = value
}

internal fun RepInterpToTokens.asToTokens(): ToTokens = value

internal fun RepInterp.asToTokens(inner: (TokenTree) -> ToTokens): ToTokens = inner(value)

/**
 * Converts a repeated token-tree list into a repetition iterator.
 */
internal class RepInterpTokenTreeList(
    val value: List<TokenTree>,
) {
    fun nextShadow(): List<TokenTree>? = value
}

internal fun RepInterpTokenTreeList.quoteIntoIter(): TokenTreeIteratorResult =
    TokenTreeIteratorResult(value.iterator(), HasIterator)

private fun mkPunct(ch: Char): Punct = Punct.new(ch, Spacing.ALONE)

private fun mkIdentDef(name: String): Ident = Ident.new(name, Span.defSite())

private fun mkIdentCall(name: String): Ident = Ident.new(name, Span.callSite())

private fun mkGroup(
    delim: Delimiter,
    stream: TokenStream,
): Group = Group.new(delim, stream)

private fun streamOf(vararg trees: TokenTree): TokenStream = TokenStream.fromTokenTrees(trees.toList())

/**
 * Builds a two-character joint operator token stream stamped with
 * [Span.defSite] on both punctuation tokens.
 */
private fun mkJointOp(
    first: Char,
    second: Char,
): TokenStream {
    val a = TokenTree.Punct(Punct.new(first, Spacing.JOINT))
    val b = TokenTree.Punct(Punct.new(second, Spacing.ALONE))
    a.setSpan(Span.defSite())
    b.setSpan(Span.defSite())
    return TokenStream.fromTokenTrees(listOf(a, b))
}

private fun mkColonColon(): TokenStream = mkJointOp(':', ':')

private fun mkFatArrow(): TokenStream = mkJointOp('=', '>')

private fun mkPlusEq(): TokenStream = mkJointOp('+', '=')

/**
 * Recursively collects all [Ident] tokens that follow a dollar punctuation
 * marker in [contentStream], preserving appearance order.
 */
private fun collectMetaVars(contentStream: TokenStream): List<Ident> {
    val vars = mutableListOf<Ident>()
    collectMetaVarsInto(contentStream, vars)
    return vars
}

private fun collectMetaVarsInto(
    stream: TokenStream,
    out: MutableList<Ident>,
) {
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
            else -> Unit
        }
    }
}

/**
 * Peekable wrapper around a [TokenTree] iterator used by [quote] and
 * [collectMetaVars].
 */
private class PeekableTokenTreeIterator(
    private val inner: Iterator<TokenTree>,
) {
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
 * Quotes a [Span] into a [TokenStream] that recovers the saved span at
 * macro evaluation time.
 */
public fun quoteSpan(
    procMacroCrate: TokenStream,
    span: Span,
): TokenStream {
    val id = span.saveSpan()
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
 * Quotes a [TokenStream] into an expression [TokenStream] whose evaluation
 * reconstructs the quoted tokens and their spans.
 */
public fun quote(stream: TokenStream): TokenStream {
    if (stream.isEmpty()) {
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
                    tokens.extendTokenStreams(
                        listOf(emitToTokensCall(streamOf(tree))),
                    )
                    continue
                }

                is TokenTree.Punct ->
                    if (tree.value.asChar() == '$') {
                        // Doubled dollar escapes to a single dollar token.
                    } else {
                        throw IllegalArgumentException(DOLLAR_FOLLOW_ERROR)
                    }

                else -> throw IllegalArgumentException(DOLLAR_FOLLOW_ERROR)
            }
        } else if (tree is TokenTree.Punct && tree.value.asChar() == '$') {
            afterDollar = true
            continue
        }

        val emitted: TokenStream =
            when (tree) {
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
 * Reads the optional separator and mandatory `*` following a repetition
 * group. Returns the separator [Punct] if present, or `null` when `*`
 * immediately follows the group.
 */
private fun readRepetitionSeparator(iter: PeekableTokenTreeIterator): Punct? {
    val first =
        if (iter.hasNext()) {
            iter.next()
        } else {
            throw IllegalArgumentException(
                REPETITION_GROUP_TRAILER_ERROR,
            )
        }
    val second = iter.peek()
    return when {
        first is TokenTree.Punct &&
            second is TokenTree.Punct &&
            first.value.spacing() == Spacing.JOINT &&
            second.value.asChar() == '*' -> {
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
 * Builds the expanded repetition block: iterator setup over all referenced
 * meta-variables followed by the loop emitting each iteration and optional
 * separator.
 */
private fun buildRepetitionExpansion(
    contents: TokenStream,
    sepOpt: Punct?,
): TokenStream {
    val repExpanded = TokenStream.new()
    val metaVars = collectMetaVars(contents)

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
        repExpanded.extendTokenTrees(
            listOf(
                TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, TokenStream.new())),
                TokenTree.Punct(mkPunct(';')),
            ),
        )
    }

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
        whileBody.extendTokenTrees(
            listOf(
                TokenTree.Group(mkGroup(Delimiter.PARENTHESIS, TokenStream.new())),
                TokenTree.Punct(mkPunct(';')),
            ),
        )
    }

    val innerQuote = quote(contents.clone())
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

private fun emitPunctReconstruction(
    charLiteralStream: TokenStream,
    spacingStream: TokenStream,
): TokenStream {
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
    val spacingStream =
        when (p.spacing()) {
            Spacing.ALONE -> spacingAloneRef()
            Spacing.JOINT -> spacingJointRef()
        }
    val charStream = streamOf(TokenTree.Literal(Literal.character(p.asChar())))
    return emitPunctReconstruction(charStream, spacingStream)
}

private fun emitGroup(g: Group): TokenStream {
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

private fun emitIdent(
    ident: Ident,
    procMacroCrate: TokenStream,
): TokenStream {
    val literal = ident.toString()
    val rawPrefix = "r#"
    val (textForLiteral, ctorName) =
        if (literal.startsWith(rawPrefix)) {
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

private fun emitLiteral(
    literal: Literal,
    procMacroCrate: TokenStream,
): TokenStream {
    val literalTextStream =
        streamOf(
            TokenTree.Literal(Literal.string(literal.toString())),
        )

    val parseCallTrees =
        mutableListOf<TokenTree>(
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

    val tuplePattern =
        TokenStream.fromTokenTrees(
            listOf(
                TokenTree.Ident(mkIdentDef("Some")),
                TokenTree.Group(
                    mkGroup(
                        Delimiter.PARENTHESIS,
                        TokenStream
                            .fromTokenTrees(
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

    val callPair =
        TokenStream.fromTokenTrees(
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

    val elseBody =
        TokenStream.fromTokenTrees(
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
