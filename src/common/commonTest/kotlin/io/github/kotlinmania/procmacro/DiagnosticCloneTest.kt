package io.github.kotlinmania.procmacro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class DiagnosticCloneTest {
    @Test
    fun emptyDiagnosticCloneRetainsItsValues() {
        val original = Diagnostic.new(Level.ERROR, "message")
        val copy = original.clone()
        assertNotSame(original, copy)
        assertEquals(Level.ERROR, copy.level())
        assertEquals("message", copy.message())
        assertEquals(0, copy.spans().size)
        assertEquals(0, copy.children().asSequence().count())
    }

    @Test
    fun clonedFieldsAndSpanListsCanChangeIndependently() {
        val firstSpan = Span(SpanData.Synthetic(10..20))
        val secondSpan = Span(SpanData.Synthetic(30..40))
        val original = Diagnostic.spanned(firstSpan.toMultiSpan(), Level.WARNING, "original")
        val copy = original.clone()
        assertNotSame(original.spans(), copy.spans())
        copy.setLevel(Level.HELP)
        copy.setMessage("copy")
        copy.setSpans(secondSpan.toMultiSpan())
        assertEquals(Level.WARNING, original.level())
        assertEquals("original", original.message())
        assertEquals(10..20, original.spans().get(0).byteRange())
        original.setMessage("changed original")
        original.setLevel(Level.NOTE)
        original.setSpans(emptyList<Span>().toMultiSpan())
        assertEquals(Level.HELP, copy.level())
        assertEquals("copy", copy.message())
        assertEquals(30..40, copy.spans().get(0).byteRange())
    }

    @Test
    fun childOrderLevelsMessagesAndSpansAreRetained() {
        val span = Span(SpanData.Synthetic(10..20))
        val original = Diagnostic.new(Level.ERROR, "root")
        original.spanNote(span.toMultiSpan(), "first")
        original.warning("second")
        original.help("third")
        val copy = original.clone()
        val children = copy.children().asSequence().toList()
        assertEquals(listOf(Level.NOTE, Level.WARNING, Level.HELP), children.map { it.level() })
        assertEquals(listOf("first", "second", "third"), children.map { it.message() })
        assertEquals(10..20, children[0].spans().get(0).byteRange())
    }

    @Test
    fun descendantsAndChildCollectionsAreClonedRecursively() {
        val original = Diagnostic.new(Level.ERROR, "root").note("child")
        val child = original.children().next().help("grandchild")
        val grandchild = child.children().next()
        val copy = original.clone()
        val copiedChild = copy.children().next()
        val copiedGrandchild = copiedChild.children().next()
        assertNotSame(child, copiedChild)
        assertNotSame(grandchild, copiedGrandchild)
        copiedChild.setMessage("changed child")
        copiedGrandchild.setMessage("changed grandchild")
        copiedChild.error("new descendant")
        copy.warning("new child")
        assertEquals("child", child.message())
        assertEquals("grandchild", grandchild.message())
        assertEquals(1, original.children().asSequence().count())
        assertEquals(1, child.children().asSequence().count())
        original.help("original child")
        assertEquals(2, copy.children().asSequence().count())
    }
}
