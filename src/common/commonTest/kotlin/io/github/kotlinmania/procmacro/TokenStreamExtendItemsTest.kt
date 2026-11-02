package io.github.kotlinmania.procmacro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class TokenStreamExtendItemsTest {
    @Test
    fun groupsAppendAsShallowGroupTokens() {
        val group = Group.new(Delimiter.BRACKET, 7.asI32ToTokens().toTokenStream())
        val stream = "prefix".asStringToTokens().toTokenStream()
        stream.extendGroups(listOf(group))
        val trees = stream.toList()
        assertEquals(2, trees.size)
        assertSame(group, assertIs<TokenTree.Group>(trees[1]).value)
        assertEquals(Delimiter.BRACKET, group.delimiter())
        assertEquals("7i32", group.stream().toString())
    }

    @Test
    fun literalsKeepTheirKindsSuffixesAndOrder() {
        val numeric = Literal.u16Suffixed(255u)
        val text = Literal.string("value")
        val stream = TokenStream.new()
        stream.extendLiterals(listOf(numeric, text))
        val trees = stream.toList()
        assertSame(numeric, assertIs<TokenTree.Literal>(trees[0]).value)
        assertSame(text, assertIs<TokenTree.Literal>(trees[1]).value)
        assertEquals("255u16", trees[0].toString())
        assertEquals("\"value\"", trees[1].toString())
    }

    @Test
    fun punctuationKeepsSpacingAndSpans() {
        val span = Span(SpanData.Synthetic(10..20))
        val first = Punct.new(':', Spacing.JOINT).also { it.setSpan(span) }
        val second = Punct.new(':', Spacing.ALONE)
        val stream = TokenStream.new()
        stream.extendPuncts(listOf(first, second))
        val trees = stream.toList()
        assertSame(first, assertIs<TokenTree.Punct>(trees[0]).value)
        assertSame(second, assertIs<TokenTree.Punct>(trees[1]).value)
        assertEquals(Spacing.JOINT, first.spacing())
        assertEquals(10..20, trees[0].span().byteRange())
        assertEquals("::", stream.toString())
    }

    @Test
    fun identifiersKeepRawNamesAndSpans() {
        val span = Span(SpanData.Synthetic(10..20))
        val first = Ident.newRaw("type", span)
        val second = Ident.new("ordinary", Span.callSite())
        val stream = TokenStream.new()
        stream.extendIdents(listOf(first, second))
        val trees = stream.toList()
        assertSame(first, assertIs<TokenTree.Ident>(trees[0]).value)
        assertSame(second, assertIs<TokenTree.Ident>(trees[1]).value)
        assertEquals("r#type ordinary", stream.toString())
        assertEquals(10..20, trees[0].span().byteRange())
    }

    @Test
    fun emptyTypedInputsLeaveExistingTokensUntouched() {
        val original = Ident.new("original", Span.callSite())
        val stream = TokenStream.fromTokenTree(TokenTree.Ident(original))
        stream.extendGroups(emptyList())
        stream.extendLiterals(emptyList())
        stream.extendPuncts(emptyList())
        stream.extendIdents(emptyList())
        assertEquals(1, stream.count())
        assertSame(original, assertIs<TokenTree.Ident>(stream.single()).value)
    }

    @Test
    fun typedExtensionConsumesTheIterableOnlyOnce() {
        val original = Ident.new("original", Span.callSite())
        var iteratorCalls = 0
        val input =
            object : Iterable<Ident> {
                override fun iterator(): Iterator<Ident> {
                    iteratorCalls++
                    check(iteratorCalls == 1)
                    return listOf(original).iterator()
                }
            }
        val stream = TokenStream.new()
        stream.extendIdents(input)
        assertEquals(1, iteratorCalls)
        assertSame(original, assertIs<TokenTree.Ident>(stream.single()).value)
    }
}
