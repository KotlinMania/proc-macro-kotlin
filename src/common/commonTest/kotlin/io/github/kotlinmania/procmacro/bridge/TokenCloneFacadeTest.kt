package io.github.kotlinmania.procmacro.bridge

import io.github.kotlinmania.procmacro.DelimSpanData
import io.github.kotlinmania.procmacro.Delimiter
import io.github.kotlinmania.procmacro.Group
import io.github.kotlinmania.procmacro.GroupData
import io.github.kotlinmania.procmacro.Ident
import io.github.kotlinmania.procmacro.LitKind
import io.github.kotlinmania.procmacro.Literal
import io.github.kotlinmania.procmacro.LiteralData
import io.github.kotlinmania.procmacro.Punct
import io.github.kotlinmania.procmacro.Spacing
import io.github.kotlinmania.procmacro.Span
import io.github.kotlinmania.procmacro.SpanData
import io.github.kotlinmania.procmacro.TokenStream
import io.github.kotlinmania.procmacro.TokenTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame

class TokenCloneFacadeTest {
    @Test
    fun facadeRetainsAllLeafDataAndIsolatesMutation() {
        val span = Span(SpanData.Synthetic(10..20))
        val trees =
            listOf(
                TokenTree.Ident(Ident.newRaw("type", span)),
                TokenTree.Punct(Punct.new(':', Spacing.JOINT).also { it.setSpan(span) }),
                TokenTree.Literal(Literal.i32Unsuffixed(-42).also { it.setSpan(span) }),
                TokenTree.Literal(Literal.u16Suffixed(255u).also { it.setSpan(span) }),
                TokenTree.Literal(Literal.string("é\n").also { it.setSpan(span) }),
                TokenTree.Literal(
                    Literal(LiteralData(LitKind.STR_RAW(2), "raw /* text */", null, span)),
                ),
                TokenTree.Literal(Literal(LiteralData(LitKind.INTEGER, "0xff", "u16", span))),
            )
        for (tree in trees) {
            val copy = cloneTokenTree(tree)
            assertNotSame(tree, copy)
            when (tree) {
                is TokenTree.Ident ->
                    assertEquals(tree.value.data, assertIs<TokenTree.Ident>(copy).value.data)
                is TokenTree.Punct ->
                    assertEquals(tree.value.data, assertIs<TokenTree.Punct>(copy).value.data)
                is TokenTree.Literal ->
                    assertEquals(tree.value.data, assertIs<TokenTree.Literal>(copy).value.data)
                is TokenTree.Group -> error("leaf expected")
            }
            copy.setSpan(Span.callSite())
            assertEquals(10..20, tree.span().byteRange())
        }
    }

    @Test
    fun facadeRetainsSeparateDelimiterSpansAndNestedGroups() {
        val entire = Span(SpanData.Synthetic(10..30))
        val open = Span(SpanData.Synthetic(10..10))
        val close = Span(SpanData.Synthetic(30..30))
        val child = TokenTree.Ident(Ident.newRaw("type", Span(SpanData.Synthetic(12..17))))
        val nested =
            Group(
                GroupData(
                    Delimiter.BRACKET,
                    TokenStream.fromTokenTree(child),
                    DelimSpanData(open = open, close = close, entire = entire),
                ),
            )
        val original =
            TokenTree.Group(
                Group(
                    GroupData(
                        Delimiter.NONE,
                        TokenStream.fromTokenTree(TokenTree.Group(nested)),
                        DelimSpanData(open = open, close = close, entire = entire),
                    ),
                ),
            )
        val copy = assertIs<TokenTree.Group>(cloneTokenTree(original)).value
        assertEquals(Delimiter.NONE, copy.delimiter())
        assertEquals(entire.byteRange(), copy.span().byteRange())
        assertEquals(open.byteRange(), copy.spanOpen().byteRange())
        assertEquals(close.byteRange(), copy.spanClose().byteRange())
        val copiedNested = assertIs<TokenTree.Group>(copy.stream().single()).value
        assertEquals(Delimiter.BRACKET, copiedNested.delimiter())
        assertEquals(open.byteRange(), copiedNested.spanOpen().byteRange())
        assertEquals(close.byteRange(), copiedNested.spanClose().byteRange())
        copiedNested.stream().single().setSpan(Span.callSite())
        copiedNested.setSpan(Span.callSite())
        copy.setSpan(Span.callSite())
        assertEquals(12..17, child.span().byteRange())
        assertEquals(10..30, nested.span().byteRange())
        assertEquals(10..30, original.span().byteRange())
    }
}
