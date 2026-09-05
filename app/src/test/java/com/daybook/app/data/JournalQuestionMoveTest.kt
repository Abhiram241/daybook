package com.daybook.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * v0.5.4 Phase 3 (§3.3) — [moveInList], the pure list-move maths behind a journal-question list
 * editor's up/down reorder buttons. Relocated to `JournalQuestionListEdits.kt` (Journal-as-habit
 * round) when the global `JournalQuestionRepository` was retired; behaviour unchanged.
 */
class JournalQuestionMoveTest {

    private val base = listOf("a", "b", "c", "d")

    @Test fun movesUp_adjacentSwap() {
        assertEquals(listOf("a", "c", "b", "d"), moveInList(base, 2, 1))
    }

    @Test fun movesDown_adjacentSwap() {
        assertEquals(listOf("b", "a", "c", "d"), moveInList(base, 0, 1))
    }

    @Test fun movesFirstToLast_orderOtherwisePreserved() {
        assertEquals(listOf("b", "c", "d", "a"), moveInList(base, 0, 3))
    }

    @Test fun sameIndex_returnsSameInstanceUnchanged() {
        assertSame(base, moveInList(base, 2, 2))
    }

    @Test fun fromOutOfRange_returnsSameInstanceUnchanged() {
        assertSame(base, moveInList(base, 4, 1))
        assertSame(base, moveInList(base, -1, 1))
    }

    @Test fun toOutOfRange_returnsSameInstanceUnchanged() {
        assertSame(base, moveInList(base, 1, 4))
        assertSame(base, moveInList(base, 1, -1))
    }

    @Test fun singleElement_isAlwaysANoOp() {
        val one = listOf("only")
        assertSame(one, moveInList(one, 0, 0))
    }
}
