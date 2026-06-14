package org.jetbrains.kotlin.kmp.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenStreamPatternTest {
    @Test
    fun predicateOrMatchesEitherSide() {
        val topLevelOnly = TokenStreamPredicate { topLevel -> topLevel }
        val nestedOnly = TokenStreamPredicate { topLevel -> !topLevel }

        val combined = topLevelOnly or nestedOnly

        assertTrue(combined.matching(topLevel = true))
        assertTrue(combined.matching(topLevel = false))
    }

    @Test
    fun firstBeforeStopsAtFirstMatch() {
        val pattern =
            FirstBefore(
                lookFor = TokenStreamPredicate { topLevel -> topLevel },
                stopAt = TokenStreamPredicate { false },
            )

        assertTrue(pattern.processToken(offset = 7, topLevel = true))
        assertEquals(7, pattern.result())
    }

    @Test
    fun firstBeforeStopsWithoutMatchWhenStopPredicateMatchesFirst() {
        val pattern =
            FirstBefore(
                lookFor = TokenStreamPredicate { false },
                stopAt = TokenStreamPredicate { topLevel -> topLevel },
            )

        assertTrue(pattern.processToken(offset = 11, topLevel = true))
        assertEquals(-1, pattern.result())
    }

    @Test
    fun lastBeforeRecordsLatestMatchBeforeStop() {
        val pattern =
            LastBefore(
                lookFor = TokenStreamPredicate { topLevel -> !topLevel },
                stopAt = TokenStreamPredicate { topLevel -> topLevel },
            )

        assertFalse(pattern.processToken(offset = 3, topLevel = false))
        assertFalse(pattern.processToken(offset = 5, topLevel = false))
        assertTrue(pattern.processToken(offset = 8, topLevel = true))
        assertEquals(5, pattern.result())
    }

    @Test
    fun lastBeforeCanAvoidStoppingRightAfterOccurrence() {
        var index = 0
        val pattern =
            LastBefore(
                lookFor = TokenStreamPredicate { index == 0 },
                stopAt = TokenStreamPredicate { topLevel -> topLevel },
                dontStopRightAfterOccurrence = true,
            )

        assertFalse(pattern.processToken(offset = 3, topLevel = false))
        index = 1
        assertFalse(pattern.processToken(offset = 4, topLevel = true))
        assertTrue(pattern.processToken(offset = 5, topLevel = true))
        assertEquals(3, pattern.result())
    }

    @Test
    fun basePatternTopLevelRequiresAllCountersClosed() {
        val pattern =
            LastBefore(
                lookFor = TokenStreamPredicate { false },
                stopAt = TokenStreamPredicate { false },
            )

        assertTrue(pattern.isTopLevel(0, 0, 0, 0))
        assertFalse(pattern.isTopLevel(1, 0, 0, 0))
        assertFalse(pattern.isTopLevel(0, 1, 0, 0))
        assertFalse(pattern.isTopLevel(0, 0, 1, 0))
        assertFalse(pattern.isTopLevel(0, 0, 0, 1))
    }
}
