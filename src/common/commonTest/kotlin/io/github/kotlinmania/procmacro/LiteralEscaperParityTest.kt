package io.github.kotlinmania.procmacro

import io.github.kotlinmania.procmacro.tracked.envVar
import io.github.kotlinmania.procmacro.tracked.path
import io.github.kotlinmania.rustcliteralescaper.EscapeError
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiteralEscaperParityTest {
    @Test
    fun strValueReportsExactEscapeErrorVariants() {
        val badEscape = Literal.fromStrUnchecked("\"\\z\"").strValue()
        val failed = assertIs<ConversionErrorKind.FailedToUnescape>(badEscape.errorOrNull())
        assertEquals(EscapeError.InvalidEscape, failed.error)

        val highHex = Literal.fromStrUnchecked("\"\\x80\"").strValue()
        val failedHex = assertIs<ConversionErrorKind.FailedToUnescape>(highHex.errorOrNull())
        assertEquals(EscapeError.OutOfRangeHexEscape, failedHex.error)

        val surrogate = Literal.fromStrUnchecked("\"\\u{D800}\"").strValue()
        val failedSurrogate = assertIs<ConversionErrorKind.FailedToUnescape>(surrogate.errorOrNull())
        assertEquals(EscapeError.LoneSurrogateUnicodeEscape, failedSurrogate.error)
    }

    @Test
    fun nonFatalLineContinuationWarningsSucceedDuringUnescaping() {
        val lit = Literal.fromStrUnchecked("\"hello\\\n \u00A0world\"")
        val text = lit.strValue().getOrThrow()
        assertEquals("hello\u00A0world", text)

        val cLit = Literal.fromStrUnchecked("c\"ab\\\n\n  cd\"")
        val cBytes = cLit.cstrValue().getOrThrow()
        assertContentEquals(
            byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 'd'.code.toByte(), 0),
            cBytes,
        )
    }

    @Test
    fun supplementaryUnicodeCharacterLiteralRoundTripsCodePointAndText() {
        val crab = Literal.characterCodePoint(0x1F980)
        assertEquals("'🦀'", crab.toString())

        val outcome = crab.characterValue()
        assertTrue(outcome.isSuccess)
        assertEquals(0x1F980, outcome.codePointOrThrow())
        assertEquals("🦀", outcome.textOrThrow())
        assertFailsWith<IllegalStateException> {
            outcome.getOrThrow()
        }

        val escapedCrab = Literal.fromStrUnchecked("'\\u{1F980}'")
        assertEquals(0x1F980, escapedCrab.characterValue().codePointOrThrow())
        assertEquals("🦀", escapedCrab.characterValue().textOrThrow())
    }

    @Test
    fun cstrValueRejectsInteriorEscapedNulAndSupportsMixedUtf8AndHighBytes() {
        val nulLit = Literal.fromStrUnchecked("c\"hi\\x00there\"")
        val nulErr = assertIs<ConversionErrorKind.FailedToUnescape>(nulLit.cstrValue().errorOrNull())
        assertEquals(EscapeError.NulInCStr, nulErr.error)

        val mixed = Literal.fromStrUnchecked("c\"🦀\\xff\"")
        val expected = "🦀".encodeToByteArray() + byteArrayOf(0xFF.toByte(), 0)
        assertContentEquals(expected, mixed.cstrValue().getOrThrow())
    }

    @Test
    fun literalFromStringValidatesRawLiteralsAndWhitespace() {
        assertTrue(Literal.fromString(" 42").isFailure)
        assertTrue(Literal.fromString("42 ").isFailure)
        assertTrue(Literal.fromString("").isFailure)

        assertEquals("r#\"ok\"#", Literal.fromStrUnchecked("r#\"ok\"#").toString())
        assertTrue(Literal.fromString("r\"bare\rcr\"").isFailure)
        assertTrue(Literal.fromString("br\"café\"").isFailure)
        assertTrue(Literal.fromString("cr\"nul\u0000byte\"").isFailure)
    }

    @Test
    fun literalSubspanResolvesSyntheticRangeAndReturnsNullOutOfBounds() {
        val lit = Literal.string("hello")
        assertNull(lit.subspan(0..2))

        lit.setSpan(Span(SpanData.Synthetic(100..106)))
        val sub = lit.subspan(1..5)
        assertEquals(101..105, sub?.byteRange())
        assertNull(lit.subspan(0..8))
    }

    @Test
    fun spanDiagnosticBuildersAndCloneMethodsPreserveValues() {
        val span = Span.callSite()
        assertEquals(Level.ERROR, span.error("err").level())
        assertEquals(Level.WARNING, span.warning("warn").level())
        assertEquals(Level.NOTE, span.note("note").level())
        assertEquals(Level.HELP, span.help("help").level())
        assertTrue(span.clone().eq(span))

        val ident = Ident.new("value", span)
        assertEquals("value", ident.clone().toString())

        val punct = Punct.new(':', Spacing.JOINT)
        assertEquals(Spacing.JOINT, punct.clone().spacing())

        val stream = TokenStream.fromString("val x = (1 + 2)").getOrThrow()
        val streamClone = stream.clone()
        assertEquals(stream.toString(), streamClone.toString())

        val iter = streamClone.iterator()
        val iterClone = iter.clone()
        assertEquals(iter.remainingCount(), iterClone.remainingCount())
        assertTrue(iter.remainingCount() > 0)
    }

    @Test
    fun trackedInputsOutsideServerReportAbsentEnvironmentVariables() {
        val missing = envVar("NON_EXISTENT_MACRO_ENV")
        assertTrue(missing.isFailure)
        assertFalse(missing.isSuccess)
        assertEquals(TrackedEnvError.NOT_PRESENT, missing.errorOrNull())
        path("Cargo.toml")
    }

    @Test
    fun toTokens128BitSuffixedHelpersEmitExpectedLiterals() {
        assertEquals("99u128", 99uL.asU128ToTokens().toTokenStream().toString())
        assertEquals("-42i128", (-42L).asI128ToTokens().toTokenStream().toString())
    }
}
