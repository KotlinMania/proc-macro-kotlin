// port-lint: ignore (smoke tests for phase-1 public types)
package io.github.kotlinmania.procmacro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DelimiterTest {
    @Test
    fun variants() {
        assertEquals(4, Delimiter.entries.size)
        assertNotEquals(Delimiter.PARENTHESIS, Delimiter.BRACE)
    }
}

class SpacingTest {
    @Test
    fun variants() {
        assertEquals(2, Spacing.entries.size)
        assertNotEquals(Spacing.JOINT, Spacing.ALONE)
    }
}

class SpanTest {
    @Test
    fun sentinelsAreDistinct() {
        assertFalse(Span.callSite().eq(Span.mixedSite()))
        assertFalse(Span.callSite().eq(Span.defSite()))
        assertFalse(Span.mixedSite().eq(Span.defSite()))
    }

    @Test
    fun sentinelByteRangesAreEmpty() {
        assertEquals(IntRange.EMPTY, Span.callSite().byteRange())
        assertEquals(IntRange.EMPTY, Span.mixedSite().byteRange())
        assertEquals(IntRange.EMPTY, Span.defSite().byteRange())
    }

    @Test
    fun localFileIsNullByDefault() {
        assertNull(Span.callSite().localFile())
    }

    @Test
    fun resolvedAtIsIdentityInPhase1() {
        val a = Span.callSite()
        val b = Span.defSite()
        // Phase-1 hygiene model is identity; phase 3 may refine.
        assertTrue(a.eq(a.resolvedAt(b)))
    }
}

class IdentTest {
    @Test
    fun roundTripsThroughToString() {
        val ident = Ident.new("hello", Span.callSite())
        assertEquals("hello", ident.toString())
    }

    @Test
    fun rawIdentTakesRhashPrefix() {
        val raw = Ident.newRaw("fn", Span.callSite())
        assertEquals("r#fn", raw.toString())
    }

    @Test
    fun rejectsEmptyIdent() {
        assertFailsWith<IllegalArgumentException> { Ident.new("", Span.callSite()) }
    }

    @Test
    fun rejectsLeadingDigit() {
        assertFailsWith<IllegalArgumentException> { Ident.new("1bad", Span.callSite()) }
    }

    @Test
    fun rejectsPathKeywordsForRaw() {
        assertFailsWith<IllegalArgumentException> { Ident.newRaw("self", Span.callSite()) }
        assertFailsWith<IllegalArgumentException> { Ident.newRaw("super", Span.callSite()) }
    }

    @Test
    fun acceptsKeywordForRaw() {
        // `fn` is a keyword but allowed as a raw identifier.
        val raw = Ident.newRaw("fn", Span.callSite())
        assertEquals("r#fn", raw.toString())
    }

    @Test
    fun setSpanReplacesSpan() {
        val ident = Ident.new("foo", Span.callSite())
        ident.setSpan(Span.mixedSite())
        assertTrue(ident.span().eq(Span.mixedSite()))
    }
}

class PunctTest {
    @Test
    fun newRecordsCharSpacingAndSpan() {
        val punct = Punct.new('+', Spacing.JOINT)
        assertEquals('+', punct.asChar())
        assertEquals(Spacing.JOINT, punct.spacing())
        assertTrue(punct.span().eq(Span.callSite()))
    }

    @Test
    fun rejectsIllegalChar() {
        assertFailsWith<IllegalArgumentException> { Punct.new('A', Spacing.ALONE) }
    }

    @Test
    fun toStringIsSingleChar() {
        assertEquals("+", Punct.new('+', Spacing.ALONE).toString())
    }

    @Test
    fun eqAgainstChar() {
        val punct = Punct.new('+', Spacing.JOINT)
        assertTrue(punct.eq('+'))
        assertFalse(punct.eq('-'))
    }
}

class LiteralTest {
    @Test
    fun stringRendersWithDoubleQuotes() {
        val literal = Literal.string("hello")
        assertEquals("\"hello\"", literal.toString())
    }

    @Test
    fun stringEscapesBackslashAndQuote() {
        assertEquals("\"a\\\\b\"", Literal.string("a\\b").toString())
        assertEquals("\"a\\\"b\"", Literal.string("a\"b").toString())
        assertEquals("\"a\\nb\"", Literal.string("a\nb").toString())
    }

    @Test
    fun characterRendersWithSingleQuotes() {
        assertEquals("'a'", Literal.character('a').toString())
        assertEquals("'\\''", Literal.character('\'').toString())
    }

    @Test
    fun u8SuffixedAppendsTypeSuffix() {
        assertEquals("42u8", Literal.u8Suffixed(42u).toString())
    }

    @Test
    fun i32UnsuffixedHasNoSuffix() {
        assertEquals("-7", Literal.i32Unsuffixed(-7).toString())
    }

    @Test
    fun f64UnsuffixedAddsDecimalPointWhenAbsent() {
        // `1.0` should keep its decimal; `1` should grow one.
        assertEquals("1.0", Literal.f64Unsuffixed(1.0).toString())
    }

    @Test
    fun byteStringEscapesNonPrintable() {
        val bytes = byteArrayOf(0x68, 0x69, 0x00, 0xff.toByte())
        assertEquals("b\"hi\\0\\xff\"", Literal.byteString(bytes).toString())
    }

    @Test
    fun stringPassesSpaceThrough() {
        // Upstream `string()` uses escape_nonascii=false, escape_double_quote=true,
        // escape_single_quote=false. Space (0x20) is printable ASCII and must
        // pass through unchanged — only the null byte renders as `\0`.
        assertEquals("\"a b c\"", Literal.string("a b c").toString())
    }

    @Test
    fun stringPassesNonAsciiThrough() {
        // escape_nonascii=false means UTF-8 code points like `é` (U+00E9)
        // render verbatim rather than as `\xNN` byte escapes.
        assertEquals("\"café\"", Literal.string("café").toString())
    }

    @Test
    fun stringDoesNotEscapeSingleQuote() {
        // String literals leave `'` unescaped per upstream EscapeOptions.
        assertEquals("\"it's\"", Literal.string("it's").toString())
    }

    @Test
    fun characterDoesNotEscapeDoubleQuote() {
        // Character literals leave `"` unescaped per upstream EscapeOptions.
        assertEquals("'\"'", Literal.character('"').toString())
    }

    @Test
    fun byteCharacterEscapesNonAscii() {
        // byte_character uses escape_nonascii=true, so 0xff renders as \xff.
        assertEquals("b'\\xff'", Literal.byteCharacter(0xff.toByte()).toString())
        // 0x00 always renders as \0.
        assertEquals("b'\\0'", Literal.byteCharacter(0x00).toString())
        // Printable ASCII passes through.
        assertEquals("b'a'", Literal.byteCharacter('a'.code.toByte()).toString())
    }

    @Test
    fun byteStringNonAsciiUsesHexEscapes() {
        // byte_string uses escape_nonascii=true: the high byte 0xc3 is not
        // re-interpreted as the lead byte of a UTF-8 sequence.
        val bytes = byteArrayOf(0xc3.toByte(), 0xa9.toByte())
        assertEquals("b\"\\xc3\\xa9\"", Literal.byteString(bytes).toString())
    }

    @Test
    fun cStringPreservesUtf8() {
        // c_string uses escape_nonascii=false, so well-formed UTF-8 round-trips
        // as Unicode code points rather than per-byte hex escapes.
        val bytes = "café".encodeToByteArray()
        assertEquals("c\"café\"", Literal.cString(bytes).toString())
    }

    @Test
    fun rejectsNonFiniteFloat() {
        assertFailsWith<IllegalArgumentException> { Literal.f32Suffixed(Float.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { Literal.f64Unsuffixed(Double.NaN) }
    }

    @Test
    fun byteCharacterValueRoundTrips() {
        val v: UByte = 0x41u
        val recovered = Literal.byteCharacter(v.toByte()).byteCharacterValue().getOrThrow()
        assertEquals(v, recovered)
    }

    @Test
    fun byteCharacterValueRecoversNullByte() {
        val recovered = Literal.byteCharacter(0x00).byteCharacterValue().getOrThrow()
        assertEquals(0u.toUByte(), recovered)
    }

    @Test
    fun byteCharacterValueRecoversHighByte() {
        val recovered = Literal.byteCharacter(0xff.toByte()).byteCharacterValue().getOrThrow()
        assertEquals(0xffu.toUByte(), recovered)
    }

    @Test
    fun byteCharacterValueRejectsWrongKind() {
        val err = Literal.string("hi").byteCharacterValue().exceptionOrNull()
        assertTrue(err is ConversionErrorKind.InvalidLiteralKind)
    }

    @Test
    fun characterValueRoundTrips() {
        val recovered = Literal.character('q').characterValue().getOrThrow()
        assertEquals('q', recovered)
    }

    @Test
    fun characterValueRecoversEscapedQuote() {
        val recovered = Literal.character('\'').characterValue().getOrThrow()
        assertEquals('\'', recovered)
    }

    @Test
    fun characterValueRejectsWrongKind() {
        val err = Literal.string("c").characterValue().exceptionOrNull()
        assertTrue(err is ConversionErrorKind.InvalidLiteralKind)
    }

    @Test
    fun strValueRoundTripsPlainString() {
        val recovered = Literal.string("hello").strValue().getOrThrow()
        assertEquals("hello", recovered)
    }

    @Test
    fun strValueRecoversEscapedSequences() {
        val recovered = Literal.string("a\\b\nc").strValue().getOrThrow()
        assertEquals("a\\b\nc", recovered)
    }

    @Test
    fun strValueRejectsWrongKind() {
        val err = Literal.character('x').strValue().exceptionOrNull()
        assertTrue(err is ConversionErrorKind.InvalidLiteralKind)
    }

    @Test
    fun cstrValueRoundTripsAppendingNul() {
        val recovered = Literal.cString("hi".encodeToByteArray()).cstrValue().getOrThrow()
        assertEquals(listOf<Byte>('h'.code.toByte(), 'i'.code.toByte(), 0), recovered.toList())
    }

    @Test
    fun cstrValueRejectsWrongKind() {
        val err = Literal.string("x").cstrValue().exceptionOrNull()
        assertTrue(err is ConversionErrorKind.InvalidLiteralKind)
    }

    @Test
    fun byteStrValueRoundTripsPlainBytes() {
        val src = byteArrayOf(1, 2, 3)
        val recovered = Literal.byteString(src).byteStrValue().getOrThrow()
        assertEquals(src.toList(), recovered.toList())
    }

    @Test
    fun byteStrValueRoundTripsEscapedBytes() {
        val src = byteArrayOf(0x00, 0x09, 0x0a, 0xff.toByte())
        val recovered = Literal.byteString(src).byteStrValue().getOrThrow()
        assertEquals(src.toList(), recovered.toList())
    }

    @Test
    fun byteStrValueRejectsWrongKind() {
        val err = Literal.character('x').byteStrValue().exceptionOrNull()
        assertTrue(err is ConversionErrorKind.InvalidLiteralKind)
    }
}

class GroupTest {
    @Test
    fun newCarriesDelimiterAndStream() {
        val inner = TokenStream.fromTokenTree(TokenTree.Ident(Ident.new("x", Span.callSite())))
        val group = Group.new(Delimiter.PARENTHESIS, inner)
        assertEquals(Delimiter.PARENTHESIS, group.delimiter())
        assertEquals("x", group.stream().toString())
    }

    @Test
    fun spanDefaultsToCallSite() {
        val group = Group.new(Delimiter.BRACE, TokenStream.new())
        assertTrue(group.span().eq(Span.callSite()))
        assertTrue(group.spanOpen().eq(Span.callSite()))
        assertTrue(group.spanClose().eq(Span.callSite()))
    }
}

class TokenTreeTest {
    @Test
    fun spanDelegatesToVariant() {
        val ident = Ident.new("name", Span.mixedSite())
        val tree: TokenTree = TokenTree.Ident(ident)
        assertTrue(tree.span().eq(Span.mixedSite()))
    }

    @Test
    fun setSpanMutatesUnderlyingPunct() {
        val tree: TokenTree = TokenTree.Punct(Punct.new('+', Spacing.ALONE))
        tree.setSpan(Span.defSite())
        assertTrue(tree.span().eq(Span.defSite()))
    }

    @Test
    fun toStringMatchesVariantToString() {
        val tree: TokenTree = TokenTree.Literal(Literal.i32Suffixed(7))
        assertEquals("7i32", tree.toString())
    }
}

class TokenStreamTest {
    @Test
    fun newIsEmpty() {
        assertTrue(TokenStream.new().isEmpty())
    }

    @Test
    fun fromTokenTreeContainsOne() {
        val stream = TokenStream.fromTokenTree(TokenTree.Ident(Ident.new("a", Span.callSite())))
        assertFalse(stream.isEmpty())
        val collected = stream.toList()
        assertEquals(1, collected.size)
        assertTrue(collected[0] is TokenTree.Ident)
    }

    @Test
    fun fromTokenTreesPreservesOrder() {
        val trees =
            listOf<TokenTree>(
                TokenTree.Ident(Ident.new("a", Span.callSite())),
                TokenTree.Punct(Punct.new('+', Spacing.ALONE)),
                TokenTree.Ident(Ident.new("b", Span.callSite())),
            )
        val stream = TokenStream.fromTokenTrees(trees)
        assertEquals(trees, stream.toList())
    }

    @Test
    fun toStringSeparatesWithSpaces() {
        val stream =
            TokenStream.fromTokenTrees(
                listOf<TokenTree>(
                    TokenTree.Ident(Ident.new("a", Span.callSite())),
                    TokenTree.Punct(Punct.new('+', Spacing.ALONE)),
                    TokenTree.Ident(Ident.new("b", Span.callSite())),
                ),
            )
        assertEquals("a + b", stream.toString())
    }

    @Test
    fun jointPunctSuppressesSeparator() {
        val stream =
            TokenStream.fromTokenTrees(
                listOf<TokenTree>(
                    TokenTree.Punct(Punct.new('+', Spacing.JOINT)),
                    TokenTree.Punct(Punct.new('=', Spacing.ALONE)),
                ),
            )
        assertEquals("+=", stream.toString())
    }

    @Test
    fun groupRenderingUsesDelimiters() {
        val inner =
            TokenStream.fromTokenTrees(
                listOf<TokenTree>(
                    TokenTree.Ident(Ident.new("x", Span.callSite())),
                    TokenTree.Punct(Punct.new(',', Spacing.ALONE)),
                    TokenTree.Ident(Ident.new("y", Span.callSite())),
                ),
            )
        val stream =
            TokenStream.fromTokenTree(
                TokenTree.Group(Group.new(Delimiter.PARENTHESIS, inner)),
            )
        assertEquals("(x , y)", stream.toString())
    }

    @Test
    fun fromStringParsesKotlinSource() {
        val result = TokenStream.fromString("fun hello() {}")
        assertTrue(result.isSuccess, "Expected fromString to succeed for valid Kotlin source")
        val stream = result.getOrThrow()
        assertFalse(stream.isEmpty(), "Expected non-empty token stream")
    }

    @Test
    fun fromStringFailsOnUnbalancedDelimiters() {
        val result = TokenStream.fromString("( unclosed")
        assertTrue(result.isFailure, "Expected fromString to fail for unbalanced delimiters")
    }

    @Test
    fun extendTokenTreesAppends() {
        val stream = TokenStream.fromTokenTree(TokenTree.Ident(Ident.new("a", Span.callSite())))
        stream.extendTokenTrees(listOf<TokenTree>(TokenTree.Punct(Punct.new('+', Spacing.ALONE))))
        assertEquals(2, stream.toList().size)
    }

    @Test
    fun extendTokenStreamsConcatenates() {
        val a = TokenStream.fromTokenTree(TokenTree.Ident(Ident.new("a", Span.callSite())))
        val b = TokenStream.fromTokenTree(TokenTree.Ident(Ident.new("b", Span.callSite())))
        a.extendTokenStreams(listOf(b))
        assertEquals(2, a.toList().size)
    }
}

class IsAvailableTest {
    @Test
    fun phase1AlwaysAvailable() {
        assertTrue(isAvailable())
    }
}

class LexErrorTest {
    @Test
    fun carriesMessage() {
        val err = LexError("nope")
        assertEquals("nope", err.toString())
    }
}

class ConversionErrorKindTest {
    @Test
    fun variantsAreDistinct() {
        val a: ConversionErrorKind = ConversionErrorKind.InvalidLiteralKind
        val b: ConversionErrorKind =
            ConversionErrorKind.FailedToUnescape(EscapeError.Fatal("boom"))
        assertNotEquals(a, b)
    }

    @Test
    fun fatalEscapeErrorReportsItself() {
        val e: EscapeError = EscapeError.Fatal("bad escape")
        assertTrue(e.isFatal())
    }

    @Test
    fun recoverableEscapeErrorReportsItself() {
        val e: EscapeError = EscapeError.Recoverable("warning")
        assertFalse(e.isFatal())
    }
}

class LevelTest {
    @Test
    fun fourVariants() {
        assertEquals(4, Level.entries.size)
        assertNotEquals(Level.ERROR, Level.WARNING)
        assertNotEquals(Level.NOTE, Level.HELP)
    }
}

class MultiSpanTest {
    @Test
    fun singleSpanWrapsAsOneElementList() {
        val span = Span.callSite()
        assertEquals(listOf(span), span.toMultiSpan().intoSpans().asList())
    }

    @Test
    fun listWrapsAsSameElements() {
        val spans = listOf(Span.callSite(), Span.mixedSite(), Span.defSite())
        assertEquals(spans, spans.toMultiSpan().intoSpans().asList())
    }

    @Test
    fun listWrapTakesDefensiveCopy() {
        val source = mutableListOf(Span.callSite())
        val multi = source.toMultiSpan()
        source.add(Span.mixedSite())
        // intoSpans() returns the snapshot taken at materialization time.
        assertEquals(1, multi.intoSpans().size)
    }
}

class DiagnosticTest {
    @Test
    fun newCarriesLevelAndMessage() {
        val diag = Diagnostic.new(Level.ERROR, "boom")
        assertEquals(Level.ERROR, diag.level())
        assertEquals("boom", diag.message())
        assertTrue(diag.spans().isEmpty())
        assertFalse(diag.children().hasNext())
    }

    @Test
    fun spannedRecordsSpans() {
        val span = Span.callSite()
        val diag = Diagnostic.spanned(span.toMultiSpan(), Level.WARNING, "watch out")
        assertEquals(Level.WARNING, diag.level())
        assertEquals("watch out", diag.message())
        assertEquals(listOf(span), diag.spans().asList())
    }

    @Test
    fun setLevelAndMessageMutateInPlace() {
        val diag = Diagnostic.new(Level.NOTE, "hi")
        diag.setLevel(Level.HELP)
        diag.setMessage("try this")
        assertEquals(Level.HELP, diag.level())
        assertEquals("try this", diag.message())
    }

    @Test
    fun setSpansReplacesViaMultiSpan() {
        val diag = Diagnostic.new(Level.ERROR, "bad")
        val replacement = listOf(Span.callSite(), Span.mixedSite())
        diag.setSpans(replacement.toMultiSpan())
        assertEquals(replacement, diag.spans().asList())
    }

    @Test
    fun chainableChildMethodsAddInOrder() {
        val parent =
            Diagnostic
                .new(Level.ERROR, "root")
                .error("err child")
                .warning("warn child")
                .note("note child")
                .help("help child")
        val kids = parent.children().asSequence().toList()
        assertEquals(4, kids.size)
        assertEquals(Level.ERROR, kids[0].level())
        assertEquals("err child", kids[0].message())
        assertEquals(Level.WARNING, kids[1].level())
        assertEquals(Level.NOTE, kids[2].level())
        assertEquals(Level.HELP, kids[3].level())
    }

    @Test
    fun spannedChildMethodsCarrySpansAndLevel() {
        val span = Span.callSite()
        val parent =
            Diagnostic
                .new(Level.ERROR, "root")
                .spanError(span.toMultiSpan(), "err here")
                .spanWarning(span.toMultiSpan(), "warn here")
                .spanNote(span.toMultiSpan(), "note here")
                .spanHelp(span.toMultiSpan(), "help here")
        val kids = parent.children().asSequence().toList()
        assertEquals(4, kids.size)
        for (kid in kids) {
            assertEquals(listOf(span), kid.spans().asList())
        }
        assertEquals(Level.ERROR, kids[0].level())
        assertEquals(Level.WARNING, kids[1].level())
        assertEquals(Level.NOTE, kids[2].level())
        assertEquals(Level.HELP, kids[3].level())
    }

    @Test
    fun childrenIteratorIsFresh() {
        val parent = Diagnostic.new(Level.ERROR, "root").note("child")
        val a = parent.children()
        val b = parent.children()
        assertTrue(a.hasNext())
        assertTrue(b.hasNext())
        a.next()
        assertFalse(a.hasNext())
        // The second iterator is independent: it still has the child.
        assertTrue(b.hasNext())
    }

    @Test
    fun emitDoesNotThrow() {
        // Phase-1 emit() renders to stdout; the only guarantee callers can
        // rely on at the unit-test level is that it does not raise.
        Diagnostic.new(Level.ERROR, "ok").error("nested").emit()
    }
}

class ToTokensTest {
    private fun streamOf(adapter: ToTokens): TokenStream {
        val s = TokenStream.new()
        adapter.toTokens(s)
        return s
    }

    @Test
    fun tokenTreeAdapterWritesSingleTree() {
        val tree: TokenTree = TokenTree.Ident(Ident.new("hello", Span.callSite()))
        assertEquals("hello", streamOf(tree.asToTokens()).toString())
    }

    @Test
    fun tokenStreamAdapterPreservesContent() {
        val source =
            TokenStream.fromTokenTrees(
                listOf(
                    TokenTree.Ident(Ident.new("a", Span.callSite())),
                    TokenTree.Punct(Punct.new(',', Spacing.ALONE)),
                    TokenTree.Ident(Ident.new("b", Span.callSite())),
                ),
            )
        assertEquals(source.toString(), streamOf(source.asToTokens()).toString())
    }

    @Test
    fun tokenStreamIntoTokenStreamReturnsSameInstance() {
        // Upstream `impl ToTokens for TokenStream` overrides `into_token_stream`
        // to return `self`; the Kotlin port returns the same TokenStream
        // reference rather than a fresh copy.
        val source =
            TokenStream.fromTokenTrees(
                listOf(TokenTree.Ident(Ident.new("x", Span.callSite()))),
            )
        assertSame(source, source.asToTokens().intoTokenStream())
    }

    @Test
    fun literalAdapterEmitsLiteralText() {
        val lit = Literal.string("hi")
        assertEquals("\"hi\"", streamOf(lit.asToTokens()).toString())
    }

    @Test
    fun identAdapterEmitsIdentifier() {
        val ident = Ident.new("alpha", Span.callSite())
        assertEquals("alpha", streamOf(ident.asToTokens()).toString())
    }

    @Test
    fun punctAdapterEmitsPunctChar() {
        val punct = Punct.new(',', Spacing.ALONE)
        assertEquals(",", streamOf(punct.asToTokens()).toString())
    }

    @Test
    fun groupAdapterEmitsDelimitedStream() {
        val inner =
            TokenStream.fromTokenTrees(
                listOf(TokenTree.Ident(Ident.new("inner", Span.callSite()))),
            )
        val group = Group.new(Delimiter.PARENTHESIS, inner)
        assertEquals("(inner)", streamOf(group.asToTokens()).toString())
    }

    @Test
    fun nullableAdapterEmitsNothingForNull() {
        val none: ToTokens? = null
        assertEquals("", streamOf(none.orEmpty()).toString())
    }

    @Test
    fun nullableAdapterForwardsForNonNull() {
        val some: ToTokens? = Ident.new("y", Span.callSite()).asToTokens()
        assertEquals("y", streamOf(some.orEmpty()).toString())
    }

    @Test
    fun ubytePrimitiveEmitsU8Suffixed() {
        assertEquals("42u8", streamOf((42u).toUByte().asU8ToTokens()).toString())
    }

    @Test
    fun ushortPrimitiveEmitsU16Suffixed() {
        assertEquals("7u16", streamOf((7u).toUShort().asU16ToTokens()).toString())
    }

    @Test
    fun uintPrimitiveEmitsU32Suffixed() {
        assertEquals("9u32", streamOf(9u.asU32ToTokens()).toString())
    }

    @Test
    fun ulongPrimitiveEmitsU64Suffixed() {
        assertEquals("11u64", streamOf(11uL.asU64ToTokens()).toString())
    }

    @Test
    fun bytePrimitiveEmitsI8Suffixed() {
        assertEquals("-3i8", streamOf((-3).toByte().asI8ToTokens()).toString())
    }

    @Test
    fun shortPrimitiveEmitsI16Suffixed() {
        assertEquals("-5i16", streamOf((-5).toShort().asI16ToTokens()).toString())
    }

    @Test
    fun intPrimitiveEmitsI32Suffixed() {
        assertEquals("-7i32", streamOf((-7).asI32ToTokens()).toString())
    }

    @Test
    fun longPrimitiveEmitsI64Suffixed() {
        assertEquals("-9i64", streamOf((-9L).asI64ToTokens()).toString())
    }

    @Test
    fun floatPrimitiveEmitsF32Suffixed() {
        assertEquals("1.5f32", streamOf(1.5f.asF32ToTokens()).toString())
    }

    @Test
    fun doublePrimitiveEmitsF64Suffixed() {
        assertEquals("2.5f64", streamOf(2.5.asF64ToTokens()).toString())
    }

    @Test
    fun booleanEmitsKeywordIdent() {
        assertEquals("true", streamOf(true.asBoolToTokens()).toString())
        assertEquals("false", streamOf(false.asBoolToTokens()).toString())
    }

    @Test
    fun charEmitsCharacterLiteral() {
        assertEquals("'a'", streamOf('a'.asCharToTokens()).toString())
    }

    @Test
    fun stringEmitsStringLiteral() {
        assertEquals("\"hi\"", streamOf("hi".asStringToTokens()).toString())
    }

    @Test
    fun byteArrayCStringEmitsCStringLiteral() {
        val bytes = "hi".encodeToByteArray()
        assertEquals("c\"hi\"", streamOf(bytes.asToTokensCString()).toString())
    }

    @Test
    fun toTokenStreamDefaultBuildsFreshStream() {
        val ident = Ident.new("z", Span.callSite())
        val first = ident.asToTokens().toTokenStream()
        val second = ident.asToTokens().toTokenStream()
        assertEquals("z", first.toString())
        assertEquals("z", second.toString())
        // Default impl returns a freshly-constructed TokenStream each call.
        assertNotSame(first, second)
    }

    @Test
    fun intoTokenStreamDefaultMatchesToTokenStream() {
        val lit = Literal.i32Suffixed(1)
        assertEquals(
            lit.asToTokens().toTokenStream().toString(),
            lit.asToTokens().intoTokenStream().toString(),
        )
    }
}

class RepetitionIteratorCheckTest {
    @Test
    fun markersAreDistinct() {
        assertNotSame(HasIterator as RepetitionIteratorCheck, ThereIsNoIteratorInRepetition)
    }

    @Test
    fun orTruthTable() {
        // Mirrors the upstream four-way BitOr impls.
        assertSame(ThereIsNoIteratorInRepetition, ThereIsNoIteratorInRepetition or ThereIsNoIteratorInRepetition)
        assertSame(HasIterator as RepetitionIteratorCheck, HasIterator or ThereIsNoIteratorInRepetition)
        assertSame(HasIterator as RepetitionIteratorCheck, ThereIsNoIteratorInRepetition or HasIterator)
        assertSame(HasIterator as RepetitionIteratorCheck, HasIterator or HasIterator)
    }
}

class RepInterpTest {
    @Test
    fun holdsValueAndYieldsItOnNextShadow() {
        val tree = TokenTree.Ident(Ident.new("value", Span.callSite()))
        val r = RepInterp(tree)
        assertSame(tree, r.value)
        assertSame(tree, r.nextShadow())
    }

    @Test
    fun iteratorAdapterForwards() {
        val a = TokenTree.Ident(Ident.new("a", Span.callSite()))
        val b = TokenTree.Ident(Ident.new("b", Span.callSite()))
        val r = RepInterpTokenTreeIterator(listOf(a, b).iterator())
        val pulled = r.iteratorAdapter().asSequence().toList()
        assertEquals(listOf(a, b), pulled)
    }

    @Test
    fun quoteIntoIterOverListWrapper() {
        val a = TokenTree.Ident(Ident.new("a", Span.callSite()))
        val b = TokenTree.Ident(Ident.new("b", Span.callSite()))
        val c = TokenTree.Ident(Ident.new("c", Span.callSite()))
        val (it, marker) = RepInterpTokenTreeList(listOf(a, b, c)).quoteIntoIter()
        assertSame(HasIterator, marker)
        assertEquals(listOf(a, b, c), it.asSequence().toList())
    }
}

class QuoteExtTest {
    @Test
    fun iteratorQuoteIntoIterReturnsHasIter() {
        val x = TokenTree.Ident(Ident.new("x", Span.callSite()))
        val (out, marker) = listOf(x).iterator().quoteIntoIter()
        assertSame(HasIterator, marker)
        assertTrue(out.hasNext())
        assertSame(x, out.next())
    }

    @Test
    fun listQuoteIntoIterReturnsHasIter() {
        val a = TokenTree.Ident(Ident.new("a", Span.callSite()))
        val b = TokenTree.Ident(Ident.new("b", Span.callSite()))
        val (out, marker) = listOf(a, b).quoteIntoIter()
        assertSame(HasIterator, marker)
        assertEquals(listOf(a, b), out.asSequence().toList())
    }

    @Test
    fun arrayQuoteIntoIterReturnsHasIter() {
        val p = TokenTree.Ident(Ident.new("p", Span.callSite()))
        val q = TokenTree.Ident(Ident.new("q", Span.callSite()))
        val (out, marker) = arrayOf<TokenTree>(p, q).quoteIntoIter()
        assertSame(HasIterator, marker)
        assertEquals(listOf(p, q), out.asSequence().toList())
    }

    @Test
    fun setQuoteIntoIterReturnsHasIter() {
        // LinkedHashSet preserves insertion order in commonMain stdlib.
        val a = TokenTree.Ident(Ident.new("a", Span.callSite()))
        val b = TokenTree.Ident(Ident.new("b", Span.callSite()))
        val src: Set<TokenTree> = linkedSetOf(a, b)
        val (out, marker) = src.quoteIntoIter()
        assertSame(HasIterator, marker)
        assertEquals(listOf(a, b), out.asSequence().toList())
    }
}

class QuoteSpanRegistryTest {
    @Test
    fun saveSpanIdRoundTrips() {
        val original = Span.mixedSite()
        val id = original.saveSpan()
        val recovered = Span.recoverProcMacroSpan(id)
        assertTrue(original.eq(recovered))
    }

    @Test
    fun missingIdThrows() {
        assertFailsWith<IllegalArgumentException> {
            Span.recoverProcMacroSpan(Int.MAX_VALUE)
        }
    }
}

class QuoteSpanTest {
    @Test
    fun quoteSpanEmbedsRecoverCall() {
        val procCrate =
            TokenStream.fromTokenTrees(
                listOf(TokenTree.Ident(Ident.new("crate", Span.defSite()))),
            )
        val rendered = quoteSpan(procCrate, Span.callSite()).toString()
        assertTrue(rendered.contains("Span"), "expected Span in: $rendered")
        assertTrue(rendered.contains("recover_proc_macro_span"), "expected recover call in: $rendered")
    }
}

class QuoteTest {
    @Test
    fun emptyInputProducesTokenStreamNewCall() {
        val out = quote(TokenStream.new()).toString()
        assertTrue(out.contains("TokenStream"), "missing TokenStream in: $out")
        assertTrue(out.contains("new"), "missing new in: $out")
    }

    @Test
    fun identQuoteIncludesIdentCtorAndOriginalText() {
        val input =
            TokenStream.fromTokenTrees(
                listOf(TokenTree.Ident(Ident.new("hello", Span.callSite()))),
            )
        val out = quote(input).toString()
        assertTrue(out.contains("\"hello\""), "expected literal hello in: $out")
        assertTrue(out.contains("Ident"), "expected Ident ctor reference in: $out")
    }

    @Test
    fun punctQuoteEmbedsPunctCtor() {
        val input =
            TokenStream.fromTokenTrees(
                listOf(TokenTree.Punct(Punct.new(',', Spacing.ALONE))),
            )
        val out = quote(input).toString()
        assertTrue(out.contains("Punct"), "expected Punct ctor in: $out")
        assertTrue(out.contains("Alone"), "expected Spacing::Alone in: $out")
        assertTrue(out.contains("','"), "expected ',' char literal in: $out")
    }

    @Test
    fun groupQuoteEmitsDelimiterAndRecursesInto() {
        val inner =
            TokenStream.fromTokenTrees(
                listOf(TokenTree.Ident(Ident.new("inside", Span.callSite()))),
            )
        val input =
            TokenStream.fromTokenTrees(
                listOf(TokenTree.Group(Group.new(Delimiter.BRACKET, inner))),
            )
        val out = quote(input).toString()
        assertTrue(out.contains("Bracket"), "expected Delimiter::Bracket in: $out")
        assertTrue(out.contains("\"inside\""), "expected nested inside literal in: $out")
    }

    @Test
    fun literalQuoteEmbedsLiteralText() {
        val input =
            TokenStream.fromTokenTrees(
                listOf(TokenTree.Literal(Literal.string("payload"))),
            )
        val out = quote(input).toString()
        assertTrue(out.contains("payload"), "expected payload text in: $out")
        assertTrue(out.contains("parse"), "expected parse call in: $out")
    }

    @Test
    fun trailingDollarRejected() {
        val input =
            TokenStream.fromTokenTrees(
                listOf(TokenTree.Punct(Punct.new('$', Spacing.ALONE))),
            )
        assertFailsWith<IllegalArgumentException> { quote(input) }
    }

    @Test
    fun dollarFollowedByGroupExpandsAsRepetition() {
        // The repetition body must declare a meta-var with `$ident` so
        // the expansion produces the per-meta-var `quote_into_iter` setup.
        val rep =
            TokenStream.fromTokenTrees(
                listOf(
                    TokenTree.Punct(Punct.new('$', Spacing.ALONE)),
                    TokenTree.Ident(Ident.new("v", Span.callSite())),
                ),
            )
        val input =
            TokenStream.fromTokenTrees(
                listOf(
                    TokenTree.Punct(Punct.new('$', Spacing.ALONE)),
                    TokenTree.Group(Group.new(Delimiter.PARENTHESIS, rep)),
                    TokenTree.Punct(Punct.new('*', Spacing.ALONE)),
                ),
            )
        val out = quote(input).toString()
        assertTrue(out.contains("while"), "expected while in repetition: $out")
        assertTrue(out.contains("quote_into_iter"), "expected quote_into_iter call: $out")
    }

    @Test
    fun dollarFollowedByGroupWithoutStarRejected() {
        val rep =
            TokenStream.fromTokenTrees(
                listOf(TokenTree.Ident(Ident.new("v", Span.callSite()))),
            )
        val input =
            TokenStream.fromTokenTrees(
                listOf(
                    TokenTree.Punct(Punct.new('$', Spacing.ALONE)),
                    TokenTree.Group(Group.new(Delimiter.PARENTHESIS, rep)),
                    TokenTree.Punct(Punct.new('+', Spacing.ALONE)),
                ),
            )
        assertFailsWith<IllegalArgumentException> { quote(input) }
    }

    @Test
    fun dollarFollowedByIdentEmitsInterpolation() {
        val input =
            TokenStream.fromTokenTrees(
                listOf(
                    TokenTree.Punct(Punct.new('$', Spacing.ALONE)),
                    TokenTree.Ident(Ident.new("var", Span.callSite())),
                ),
            )
        val out = quote(input).toString()
        assertTrue(out.contains("ToTokens"), "expected ToTokens dispatch: $out")
        assertTrue(out.contains("var"), "expected interpolated var ident: $out")
    }

    @Test
    fun dollarDollarPassesThroughAsLiteralDollar() {
        val input =
            TokenStream.fromTokenTrees(
                listOf(
                    TokenTree.Punct(Punct.new('$', Spacing.ALONE)),
                    TokenTree.Punct(Punct.new('$', Spacing.ALONE)),
                ),
            )
        // No throw: the second dollar resets afterDollar and the loop
        // moves on without emitting the escape.
        quote(input)
    }
}

// --- KtTokenAdapter / TokenStream.fromString integration tests ---

class KtTokenAdapterTest {
    @Test
    fun fromStringParsesSimpleFunction() {
        val result = TokenStream.fromString("fun hello() {}")
        assertTrue(result.isSuccess)
        val stream = result.getOrThrow()
        val trees = stream.toList()
        // fun -> Ident, hello -> Ident, ( -> Group(PARENTHESIS), { } -> Group(BRACE)
        assertTrue(trees.size >= 3, "Expected at least 3 top-level trees, got ${trees.size}")
    }

    @Test
    fun fromStringMapsKeywordsToIdents() {
        val result = TokenStream.fromString("class Foo")
        assertTrue(result.isSuccess)
        val trees = result.getOrThrow().toList()
        // "class" is a hard keyword → Ident("class"), "Foo" → Ident("Foo")
        val idents = trees.filterIsInstance<TokenTree.Ident>()
        assertTrue(idents.any { it.value.toString() == "class" }, "Expected 'class' as Ident")
        assertTrue(idents.any { it.value.toString() == "Foo" }, "Expected 'Foo' as Ident")
    }

    @Test
    fun fromStringDecomposesArrow() {
        val result = TokenStream.fromString("->")
        assertTrue(result.isSuccess)
        val trees = result.getOrThrow().toList()
        // ARROW (->) decomposes into Punct('-', JOINT) + Punct('>', ALONE)
        assertEquals(2, trees.size)
        val first = trees[0] as TokenTree.Punct
        val second = trees[1] as TokenTree.Punct
        assertEquals('-', first.value.asChar())
        assertEquals(Spacing.JOINT, first.value.spacing())
        assertEquals('>', second.value.asChar())
        assertEquals(Spacing.ALONE, second.value.spacing())
    }

    @Test
    fun fromStringDecomposesEqEq() {
        val result = TokenStream.fromString("==")
        assertTrue(result.isSuccess)
        val trees = result.getOrThrow().toList()
        assertEquals(2, trees.size)
        val first = trees[0] as TokenTree.Punct
        val second = trees[1] as TokenTree.Punct
        assertEquals('=', first.value.asChar())
        assertEquals(Spacing.JOINT, first.value.spacing())
        assertEquals('=', second.value.asChar())
        assertEquals(Spacing.ALONE, second.value.spacing())
    }

    @Test
    fun fromStringSinglePunctIsAlone() {
        val result = TokenStream.fromString("+")
        assertTrue(result.isSuccess)
        val trees = result.getOrThrow().toList()
        assertEquals(1, trees.size)
        val punct = trees[0] as TokenTree.Punct
        assertEquals('+', punct.value.asChar())
        assertEquals(Spacing.ALONE, punct.value.spacing())
    }

    @Test
    fun fromStringParsesStringLiteral() {
        val result = TokenStream.fromString("\"hello\"")
        assertTrue(result.isSuccess)
        val trees = result.getOrThrow().toList()
        assertEquals(1, trees.size)
        val lit = trees[0] as TokenTree.Literal
        assertTrue(
            lit.value.toString().contains("hello"),
            "Expected string literal containing 'hello', got ${lit.value}",
        )
    }

    @Test
    fun fromStringParsesIntegerLiteral() {
        val result = TokenStream.fromString("42")
        assertTrue(result.isSuccess)
        val trees = result.getOrThrow().toList()
        assertEquals(1, trees.size)
        val lit = trees[0] as TokenTree.Literal
        assertTrue(lit.value.toString().contains("42"), "Expected integer literal, got ${lit.value}")
    }

    @Test
    fun fromStringParsesFloatLiteral() {
        val result = TokenStream.fromString("3.14")
        assertTrue(result.isSuccess)
        val trees = result.getOrThrow().toList()
        assertEquals(1, trees.size)
        val lit = trees[0] as TokenTree.Literal
        assertTrue(lit.value.toString().contains("3.14"), "Expected float literal, got ${lit.value}")
    }

    @Test
    fun fromStringNestsParenGroups() {
        val result = TokenStream.fromString("(a, b)")
        assertTrue(result.isSuccess)
        val trees = result.getOrThrow().toList()
        assertEquals(1, trees.size)
        val group = trees[0] as TokenTree.Group
        assertEquals(Delimiter.PARENTHESIS, group.value.delimiter())
        val inner = group.value.stream().toList()
        // a , b → Ident Punct COMMA Punct Ident (comma and idents)
        assertTrue(inner.any { it is TokenTree.Ident }, "Expected Ident inside group")
    }

    @Test
    fun fromStringNestsBraceGroups() {
        val result = TokenStream.fromString("{ x }")
        assertTrue(result.isSuccess)
        val trees = result.getOrThrow().toList()
        assertEquals(1, trees.size)
        val group = trees[0] as TokenTree.Group
        assertEquals(Delimiter.BRACE, group.value.delimiter())
    }

    @Test
    fun fromStringNestedGroups() {
        val result = TokenStream.fromString("f({ g })")
        assertTrue(result.isSuccess)
        val trees = result.getOrThrow().toList()
        // f → Ident, ( → Group(PAREN), containing Group(BRACE)
        assertTrue(trees.size >= 2, "Expected at least 2 top-level trees")
        val outerGroup =
            trees.filterIsInstance<TokenTree.Group>().firstOrNull()
                ?: error("Expected a Group in the result")
        assertEquals(Delimiter.PARENTHESIS, outerGroup.value.delimiter())
        val inner = outerGroup.value.stream().toList()
        val innerBrace =
            inner.filterIsInstance<TokenTree.Group>().firstOrNull()
                ?: error("Expected nested brace group")
        assertEquals(Delimiter.BRACE, innerBrace.value.delimiter())
    }

    @Test
    fun fromStringFailsOnUnbalancedParen() {
        val result = TokenStream.fromString("( unclosed")
        assertTrue(result.isFailure)
    }

    @Test
    fun fromStringFailsOnExtraCloseParen() {
        val result = TokenStream.fromString(")")
        assertTrue(result.isFailure)
    }

    @Test
    fun fromStringQuestDotAreSeparateTokens() {
        val result = TokenStream.fromString("?.")
        assertTrue(result.isSuccess)
        val trees = result.getOrThrow().toList()
        // Lexer produces QUEST + DOT separately
        assertEquals(2, trees.size)
        val quest = trees[0] as TokenTree.Punct
        val dot = trees[1] as TokenTree.Punct
        assertEquals('?', quest.value.asChar())
        assertEquals(Spacing.ALONE, quest.value.spacing())
        assertEquals('.', dot.value.asChar())
        assertEquals(Spacing.ALONE, dot.value.spacing())
    }

    @Test
    fun fromStringDecomposesRangeUntil() {
        val result = TokenStream.fromString("..<")
        assertTrue(result.isSuccess)
        val trees = result.getOrThrow().toList()
        // RANGE_UNTIL (..<) decomposes into Punct('.', JOINT), Punct('.', JOINT), Punct('<', ALONE)
        assertEquals(3, trees.size)
        assertEquals('.', (trees[0] as TokenTree.Punct).value.asChar())
        assertEquals(Spacing.JOINT, (trees[0] as TokenTree.Punct).value.spacing())
        assertEquals('.', (trees[1] as TokenTree.Punct).value.asChar())
        assertEquals(Spacing.JOINT, (trees[1] as TokenTree.Punct).value.spacing())
        assertEquals('<', (trees[2] as TokenTree.Punct).value.asChar())
        assertEquals(Spacing.ALONE, (trees[2] as TokenTree.Punct).value.spacing())
    }

    @Test
    fun fromStringDecomposesNotIn() {
        val result = TokenStream.fromString("!in")
        assertTrue(result.isSuccess)
        val trees = result.getOrThrow().toList()
        // NOT_IN → Punct('!', ALONE) + Ident("in")
        assertEquals(2, trees.size)
        val punct = trees[0] as TokenTree.Punct
        assertEquals('!', punct.value.asChar())
        val ident = trees[1] as TokenTree.Ident
        assertEquals("in", ident.value.toString())
    }

    @Test
    fun fromStringFiltersWhitespace() {
        val result = TokenStream.fromString("  a  b  ")
        assertTrue(result.isSuccess)
        val trees = result.getOrThrow().toList()
        assertEquals(2, trees.size, "Expected 2 idents after whitespace filtering")
    }

    @Test
    fun fromStringFiltersComments() {
        val result = TokenStream.fromString("a // comment\nb")
        assertTrue(result.isSuccess)
        val trees = result.getOrThrow().toList()
        assertEquals(2, trees.size, "Expected 2 idents after comment filtering")
    }

    @Test
    fun fromStringParsesCharacterLiteral() {
        val result = TokenStream.fromString("'x'")
        assertTrue(result.isSuccess)
        val trees = result.getOrThrow().toList()
        assertEquals(1, trees.size)
        val lit = trees[0] as TokenTree.Literal
        assertTrue(lit.value.toString().contains("x"), "Expected char literal, got ${lit.value}")
    }
}
