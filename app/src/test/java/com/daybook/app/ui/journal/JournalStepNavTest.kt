package com.daybook.app.ui.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.4 Phase 4 (§4.4) — pins the conversational-flow step maths: [clampIndex] keeps `next()` /
 * `back()` inside bounds (and returns 0 for an empty step list), [isLastStep] gates Save vs Next.
 */
class JournalStepNavTest {

    @Test fun clampIndex_withinBounds_isUnchanged() {
        assertEquals(0, clampIndex(0, 5))
        assertEquals(3, clampIndex(3, 5))
        assertEquals(4, clampIndex(4, 5))
    }

    @Test fun next_atLastStep_staysOnLast() {
        // back() / next() feed (index ± 1); at the last step next() must not overshoot.
        assertEquals(4, clampIndex(5, 5))
        assertEquals(4, clampIndex(4 + 1, 5))
    }

    @Test fun back_atStepZero_staysAtZero() {
        assertEquals(0, clampIndex(-1, 5))
        assertEquals(0, clampIndex(0 - 1, 5))
    }

    @Test fun clampIndex_emptyStepList_isZero() {
        assertEquals(0, clampIndex(0, 0))
        assertEquals(0, clampIndex(3, 0))
        assertEquals(0, clampIndex(-2, 0))
    }

    @Test fun clampIndex_singleStep_isAlwaysZero() {
        assertEquals(0, clampIndex(0, 1))
        assertEquals(0, clampIndex(1, 1))
        assertEquals(0, clampIndex(-1, 1))
    }

    @Test fun isLastStep_trueOnlyAtFinalIndex() {
        assertFalse(isLastStep(0, 3))
        assertFalse(isLastStep(1, 3))
        assertTrue(isLastStep(2, 3))
    }

    @Test fun isLastStep_singleStep_isImmediatelyLast() {
        assertTrue(isLastStep(0, 1))
    }

    @Test fun isLastStep_emptyList_isFalse() {
        assertFalse(isLastStep(0, 0))
    }

    @Test fun isLastStep_indexPastEnd_isStillLast() {
        // Defensive: a stale index must not read as "more steps to go".
        assertTrue(isLastStep(9, 3))
    }
}
