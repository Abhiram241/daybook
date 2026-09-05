package com.daybook.app.ui.routines

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task C (C1/C2) — [clampStreakStart] defense-in-depth: a picked start instant is never allowed
 * past `now`, even though the date picker's own `maxDate` already prevents picking a future date
 * structurally. The date picker only constrains the CALENDAR DAY; this guards the exact instant.
 */
class ClampStreakStartTest {

    @Test fun pastPickedInstant_isUnchanged() {
        val now = 1_770_000_000_000L
        val threeDaysAgo = now - 3 * 24 * 60 * 60 * 1000L
        assertEquals(threeDaysAgo, clampStreakStart(threeDaysAgo, now))
    }

    @Test fun futurePickedInstant_isClampedToNow() {
        val now = 1_770_000_000_000L
        val future = now + 60_000L
        assertEquals(now, clampStreakStart(future, now))
    }

    @Test fun exactlyNow_isUnchanged() {
        val now = 1_770_000_000_000L
        assertEquals(now, clampStreakStart(now, now))
    }
}
