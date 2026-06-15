// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SyntaxElementTypeSetTest {
    @Test
    fun sumOfSetsWithSameElement() {
        val item1 = SyntaxElementType("item1")
        val item2 = SyntaxElementType("item2")
        val item3 = SyntaxElementType("item3")

        val set1 = syntaxElementTypeSetOf(item1, item2)
        val set2 = syntaxElementTypeSetOf(item2, item3)

        val sum = set1 + set2

        assertEquals(3, sum.size)

        val iterated = mutableSetOf<SyntaxElementType>()
        for (elementType in sum) {
            assertTrue(iterated.add(elementType), "Duplicate element type: $elementType")
        }
    }

    @Test
    fun emptySetOperations() {
        val emptySet = emptySyntaxElementTypeSet()
        assertTrue(emptySet.isEmpty())
        assertEquals(0, emptySet.size)
    }

    @Test
    fun containsAndContainsAll() {
        val item1 = SyntaxElementType("item1")
        val item2 = SyntaxElementType("item2")
        val set = syntaxElementTypeSetOf(item1, item2)

        assertTrue(set.contains(item1))
        assertTrue(set.contains(item2))
        assertTrue(set.containsAll(listOf(item1, item2)))
    }

    @Test
    fun plusSingleElement() {
        val item1 = SyntaxElementType("item1")
        val item2 = SyntaxElementType("item2")
        val set = syntaxElementTypeSetOf(item1)

        val newSet = set + item2
        assertEquals(2, newSet.size)
        assertTrue(newSet.contains(item1))
        assertTrue(newSet.contains(item2))
    }

    @Test
    fun isEmpty() {
        val emptySet = emptySyntaxElementTypeSet()
        assertTrue(emptySet.isEmpty())

        val nonEmptySet = syntaxElementTypeSetOf(SyntaxElementType("test"))
        assertFalse(nonEmptySet.isEmpty())
    }

    @Test
    fun nullElementHandling() {
        val set = syntaxElementTypeSetOf(SyntaxElementType("test"))
        assertFalse(set.containsNullable(null))
    }

    @Test
    fun containsWithDuplicateElements() {
        val item = SyntaxElementType("duplicate")
        val set = syntaxElementTypeSetOf(item, item)

        assertEquals(1, set.size)
        assertTrue(set.contains(item))
    }

    @Test
    fun containsWithMultipleChecks() {
        val item = SyntaxElementType("test")
        val set = syntaxElementTypeSetOf(item)

        assertTrue(set.contains(item))
        assertTrue(set.contains(item))
        assertTrue(set.contains(item))
    }

    @Test
    fun containsWithNonexistentElement() {
        val item = SyntaxElementType("existing")
        val nonExistent = SyntaxElementType("non-existent")
        val set = syntaxElementTypeSetOf(item)

        assertFalse(set.contains(nonExistent))
    }

    @Test
    fun plusEmptyIterable() {
        val item = SyntaxElementType("test")
        val set = syntaxElementTypeSetOf(item)
        val result = set + emptyList()

        assertEquals(1, result.size)
        assertTrue(result.contains(item))
    }

    @Test
    fun plusSingleElementIterable() {
        val item1 = SyntaxElementType("item1")
        val item2 = SyntaxElementType("item2")
        val set = syntaxElementTypeSetOf(item1)
        val result = set + listOf(item2)

        assertEquals(2, result.size)
        assertTrue(result.contains(item1))
        assertTrue(result.contains(item2))
    }

    @Test
    fun plusMultipleSets() {
        val item1 = SyntaxElementType("item1")
        val item2 = SyntaxElementType("item2")
        val item3 = SyntaxElementType("item3")

        val set1 = syntaxElementTypeSetOf(item1)
        val set2 = syntaxElementTypeSetOf(item2)
        val set3 = syntaxElementTypeSetOf(item3)

        val result = set1 + set2 + set3

        assertEquals(3, result.size)
        assertTrue(result.containsAll(listOf(item1, item2, item3)))
    }

    @Test
    fun plusWithDuplicateElementsInIterable() {
        val item1 = SyntaxElementType("item1")
        val item2 = SyntaxElementType("item2")
        val set = syntaxElementTypeSetOf(item1)
        val result = set + listOf(item2, item2, item2)

        assertEquals(2, result.size)
        assertTrue(result.contains(item1))
        assertTrue(result.contains(item2))
    }

    @Test
    fun plusWithEmptySet() {
        val item = SyntaxElementType("test")
        val set = syntaxElementTypeSetOf(item)
        val emptySet = emptySyntaxElementTypeSet()

        val result = set + emptySet
        assertEquals(1, result.size)
        assertTrue(result.contains(item))
    }

    @Test
    fun plusWithMultipleNewElements() {
        val initial = SyntaxElementType("initial")
        val new1 = SyntaxElementType("new1")
        val new2 = SyntaxElementType("new2")
        val new3 = SyntaxElementType("new3")

        val set = syntaxElementTypeSetOf(initial)
        val result = set + new1 + new2 + new3

        assertEquals(4, result.size)
        assertTrue(result.containsAll(listOf(initial, new1, new2, new3)))
    }

    @Test
    fun plusWithSameElement() {
        val initial = SyntaxElementType("initial")

        val set = syntaxElementTypeSetOf(initial)
        val result = set + initial

        assertEquals(1, result.size)
    }

    @Test
    fun plusWithNullElementHandling() {
        val item = SyntaxElementType("test")
        val set = syntaxElementTypeSetOf(item)
        val nullList: List<SyntaxElementType?> = listOf(null)

        val result = set + nullList

        assertFalse(result is SyntaxElementTypeSet)
        assertEquals(2, result.size)
        assertTrue(result.contains(item))
        assertTrue(result.contains(null))
    }

    @Test
    fun spreadOperatorKeepsAllElements() {
        val array = arrayOf(1, 2, 3)
        val set = setOf(*array, 4)
        assertEquals(4, set.size)
    }

    @Test
    fun minusSingleElement() {
        val item1 = SyntaxElementType("item1")
        val item2 = SyntaxElementType("item2")
        val set = syntaxElementTypeSetOf(item1, item2)

        val result = set - item1
        assertEquals(1, result.size)
        assertTrue(item2 in result)
        assertFalse(item1 in result)
    }

    @Test
    fun minusMultipleElements() {
        val item1 = SyntaxElementType("item1")
        val item2 = SyntaxElementType("item2")
        val item3 = SyntaxElementType("item3")
        val set = syntaxElementTypeSetOf(item1, item2, item3)

        val result = set - listOf(item1, item2)
        assertEquals(1, result.size)
        assertTrue(item3 in result)
        assertFalse(item1 in result)
        assertFalse(item2 in result)
    }

    @Test
    fun minusNonexistentElement() {
        val item = SyntaxElementType("existing")
        val nonExistent = SyntaxElementType("non-existent")
        val set = syntaxElementTypeSetOf(item)

        val result = set - nonExistent
        assertEquals(1, result.size)
        assertTrue(item in result)
    }

    @Test
    fun minusFromEmptySet() {
        val item = SyntaxElementType("test")
        val emptySet = emptySyntaxElementTypeSet()

        val result = emptySet - item
        assertTrue(result.isEmpty())
    }

    @Test
    fun minusWithNullElementHandling() {
        val item = SyntaxElementType("test")
        val set = syntaxElementTypeSetOf(item)
        val nullList: List<SyntaxElementType?> = listOf(null)

        val result = set - nullList
        assertEquals(1, result.size)
        assertTrue(item in result)
    }

    @Test
    fun intersectWithEmptySet() {
        val item = SyntaxElementType("test")
        val set = syntaxElementTypeSetOf(item)
        val emptySet = emptySyntaxElementTypeSet()

        val result = set.intersect(emptySet)
        assertTrue(result.isEmpty())
    }

    @Test
    fun intersectWithSameElements() {
        val item1 = SyntaxElementType("item1")
        val item2 = SyntaxElementType("item2")
        val set1 = syntaxElementTypeSetOf(item1, item2)
        val set2 = syntaxElementTypeSetOf(item1, item2)

        val result = set1.intersect(set2)
        assertEquals(2, result.size)
        assertTrue(result.containsAll(listOf(item1, item2)))
    }

    @Test
    fun intersectWithOverlappingElements() {
        val item1 = SyntaxElementType("item1")
        val item2 = SyntaxElementType("item2")
        val item3 = SyntaxElementType("item3")
        val set1 = syntaxElementTypeSetOf(item1, item2)
        val set2 = syntaxElementTypeSetOf(item2, item3)

        val result = set1.intersect(set2)
        assertEquals(1, result.size)
        assertTrue(item2 in result)
    }

    @Test
    fun intersectWithDisjointSets() {
        val item1 = SyntaxElementType("item1")
        val item2 = SyntaxElementType("item2")
        val set1 = syntaxElementTypeSetOf(item1)
        val set2 = syntaxElementTypeSetOf(item2)

        val result = set1.intersect(set2)
        assertTrue(result.isEmpty())
    }
}
