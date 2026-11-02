package io.github.kotlinmania.procmacro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame

class GroupStreamOwnershipTest {
    @Test
    fun returnedStreamDoesNotMutateGroupContents() {
        val group =
            Group.new(
                Delimiter.PARENTHESIS,
                Ident.new("original", Span.callSite()).asToTokens().toTokenStream(),
            )
        val first = group.stream()
        first.extendTokenTrees(listOf(TokenTree.Ident(Ident.new("added", Span.callSite()))))
        assertEquals("original added", first.toString())
        assertEquals("original", group.stream().toString())
        assertEquals("(original)", group.toString())
        assertNotSame(first, group.stream())
    }

    @Test
    fun returnedLeafSpansAreIndependent() {
        val span = Span(SpanData.Synthetic(10..20))
        val tree = TokenTree.Ident(Ident.new("original", span))
        val group = Group.new(Delimiter.BRACKET, TokenStream.fromTokenTree(tree))
        val first = group.stream().single()
        val second = group.stream().single()
        first.setSpan(Span.callSite())
        assertNotSame(first, second)
        assertEquals(10..20, tree.span().byteRange())
        assertEquals(10..20, second.span().byteRange())
    }

    @Test
    fun nestedGroupContentsAndDelimiterSpansAreIndependent() {
        val span = Span(SpanData.Synthetic(10..20))
        val inner =
            Group.new(
                Delimiter.BRACKET,
                TokenStream.fromTokenTree(TokenTree.Ident(Ident.new("nested", span))),
            )
        inner.setSpan(span)
        val outer = Group.new(Delimiter.BRACE, TokenStream.fromTokenTree(TokenTree.Group(inner)))
        val returned = assertIs<TokenTree.Group>(outer.stream().single()).value
        returned.setSpan(Span.callSite())
        val returnedStream = returned.data.stream
        returnedStream.single().setSpan(Span.callSite())
        returnedStream.extendTokenTrees(listOf(TokenTree.Ident(Ident.new("added", Span.callSite()))))
        val retained = assertIs<TokenTree.Group>(outer.stream().single()).value
        assertEquals(10..20, retained.spanOpen().byteRange())
        assertEquals(10..20, retained.spanClose().byteRange())
        val retainedChild = retained.stream().single()
        assertEquals(10..20, retainedChild.span().byteRange())
        assertEquals("nested", retained.stream().toString())
    }

    @Test
    fun emptyGroupReturnsFreshEmptyStreams() {
        val group = Group.new(Delimiter.NONE, TokenStream.new())
        val first = group.stream()
        val second = group.stream()
        assertNotSame(first, second)
        first.extendTokenTrees(listOf(TokenTree.Ident(Ident.new("added", Span.callSite()))))
        assertEquals("", second.toString())
        assertEquals("", group.toString())
    }
}
