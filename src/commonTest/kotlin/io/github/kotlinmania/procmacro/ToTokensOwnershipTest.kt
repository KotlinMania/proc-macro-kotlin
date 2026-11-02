package io.github.kotlinmania.procmacro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ToTokensOwnershipTest {
    private val originalSpan = Span(SpanData.Synthetic(10..20))

    @Test
    fun tokenTreeEmissionClonesEachLeaf() {
        val trees =
            listOf(
                TokenTree.Ident(Ident.newRaw("type", originalSpan)),
                TokenTree.Punct(Punct.new(':', Spacing.JOINT).also { it.setSpan(originalSpan) }),
                TokenTree.Literal(Literal.i32Suffixed(42).also { it.setSpan(originalSpan) }),
            )
        for (tree in trees) {
            val emitted = tree.asToTokens().toTokenStream().single()
            assertNotSame(tree, emitted)
            assertEquals(tree.toString(), emitted.toString())
            emitted.setSpan(Span.callSite())
            assertEquals(10..20, tree.span().byteRange())
        }
    }

    @Test
    fun leafAdaptersCloneAtEmissionTime() {
        val ident = Ident.newRaw("type", originalSpan)
        val punct = Punct.new(':', Spacing.JOINT).also { it.setSpan(originalSpan) }
        val literal = Literal.u16Suffixed(255u).also { it.setSpan(originalSpan) }
        val cases =
            listOf(
                ident.asToTokens() to TokenTree.Ident(ident),
                punct.asToTokens() to TokenTree.Punct(punct),
                literal.asToTokens() to TokenTree.Literal(literal),
            )
        for ((adapter, source) in cases) {
            val first = adapter.toTokenStream().single()
            first.setSpan(Span.callSite())
            assertEquals(10..20, source.span().byteRange())
            source.setSpan(Span.mixedSite())
            val second = adapter.toTokenStream().single()
            assertSame(SpanData.MixedSite, second.span().data)
            assertSame(SpanData.CallSite, first.span().data)
        }
    }

    @Test
    fun groupAdapterClonesNestedTokensAndDelimiterSpans() {
        val child = TokenTree.Ident(Ident.new("child", originalSpan))
        val group = Group.new(Delimiter.BRACKET, TokenStream.fromTokenTree(child))
        group.setSpan(originalSpan)
        val emitted = assertIs<TokenTree.Group>(group.asToTokens().toTokenStream().single()).value
        assertNotSame(group, emitted)
        assertEquals(group.toString(), emitted.toString())
        emitted.setSpan(Span.callSite())
        emitted.stream().single().setSpan(Span.callSite())
        assertEquals(10..20, group.span().byteRange())
        assertEquals(10..20, group.spanOpen().byteRange())
        assertEquals(10..20, group.spanClose().byteRange())
        assertEquals(10..20, child.span().byteRange())
    }

    @Test
    fun streamAdapterClonesItsTokens() {
        val child = TokenTree.Ident(Ident.new("child", originalSpan))
        val source = TokenStream.fromTokenTree(child)
        val emitted = source.asToTokens().toTokenStream()
        emitted.single().setSpan(Span.callSite())
        source.extendTokenTrees(listOf(TokenTree.Ident(Ident.new("later", Span.callSite()))))
        assertEquals(10..20, child.span().byteRange())
        assertEquals("child", emitted.toString())
        assertEquals("child later", source.toString())
    }

    @Test
    fun appendingAStreamToItselfUsesAnIndependentSnapshot() {
        val source = TokenStream.fromTokenTree(TokenTree.Ident(Ident.new("child", originalSpan)))
        source.asToTokens().toTokens(source)
        assertEquals("child child", source.toString())
        val trees = source.toList()
        assertNotSame(trees[0], trees[1])
        trees[1].setSpan(Span.callSite())
        assertEquals(10..20, trees[0].span().byteRange())
    }

    @Test
    fun consumingTreeConversionKeepsTheOwnedTree() {
        val tree = TokenTree.Ident(Ident.new("owned", originalSpan))
        assertSame(tree, tree.asToTokens().intoTokenStream().single())
    }
}
