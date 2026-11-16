package io.github.kotlinmania.procmacro

import io.github.kotlinmania.procmacro.bridge.Result
import io.github.kotlinmania.procmacro.bridge.Symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class IdentifierCrateTest {
    @Test
    fun ordinaryCrateIdentifierRetainsTextAndSpan() {
        val span = Span(SpanData.Synthetic(10..20))
        val ident = Ident.new("\$crate", span)
        assertEquals("\$crate", ident.toString())
        assertSame(span, ident.span())
    }

    @Test
    fun bridgeAcceptsTheOrdinaryCrateIdentifier() {
        val result = Symbol.normalizeAndValidateIdent("\$crate")
        assertEquals("\$crate", assertIs<Result.Ok<Symbol>>(result).value.asString())
    }

    @Test
    fun rawCrateIdentifierRemainsRejected() {
        assertFailsWith<IllegalArgumentException> { Ident.newRaw("\$crate", Span.callSite()) }
        assertIs<Result.Err>(Symbol.normalizeAndValidateRawIdent("\$crate"))
    }

    @Test
    fun otherDollarIdentifiersRemainRejected() {
        for (name in listOf("\$other", "\$crateSuffix", "prefix\$crate", "\$")) {
            assertFailsWith<IllegalArgumentException> { Ident.new(name, Span.callSite()) }
            assertIs<Result.Err>(Symbol.normalizeAndValidateIdent(name))
        }
    }

    @Test
    fun rawPathKeywordsAndUnderscoreRemainRejected() {
        for (name in listOf("_", "self", "super", "Self", "crate")) {
            assertFailsWith<IllegalArgumentException> { Ident.newRaw(name, Span.callSite()) }
            assertIs<Result.Err>(Symbol.normalizeAndValidateRawIdent(name))
        }
    }
}
