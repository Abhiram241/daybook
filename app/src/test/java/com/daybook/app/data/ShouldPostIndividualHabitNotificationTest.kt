package com.daybook.app.data

import com.daybook.app.data.model.HabitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX overhaul item 3 — the pure predicate that decides whether an occurrence should post its own
 * per-item habit notification. Only [HabitType.BATCH] must be suppressed; everything else is
 * unchanged (the narrow-bugfix guarantee).
 */
class ShouldPostIndividualHabitNotificationTest {

    @Test
    fun `BATCH occurrences never post an individual notification`() {
        assertFalse(shouldPostIndividualHabitNotification(HabitType.BATCH))
    }

    @Test
    fun `INDIVIDUAL and JOURNAL still post their own notification`() {
        assertTrue(shouldPostIndividualHabitNotification(HabitType.INDIVIDUAL))
        assertTrue(shouldPostIndividualHabitNotification(HabitType.JOURNAL))
    }

    @Test
    fun `STREAK is not BATCH so the predicate is true (it has no occurrences anyway)`() {
        assertTrue(shouldPostIndividualHabitNotification(HabitType.STREAK))
    }

    @Test
    fun `exactly one habit type is suppressed`() {
        assertEquals(1L, HabitType.entries.count { !shouldPostIndividualHabitNotification(it) }.toLong())
    }
}
