package com.daybook.app.ui.routines

import com.daybook.app.data.model.HabitType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.5 — [habitFormSaveEnabled]: INDIVIDUAL needs a title AND ≥1 reminder time; BATCH and
 * STREAK ("Ongoing") need only a non-blank title. Journal-as-habit round: JOURNAL schedules
 * exactly like INDIVIDUAL (B4), so it needs ≥1 reminder time too.
 */
class HabitFormSaveEnabledTest {

    @Test fun individual_needsTitleAndTime() {
        assertFalse(habitFormSaveEnabled("Drink water", HabitType.INDIVIDUAL, timeCount = 0))
        assertTrue(habitFormSaveEnabled("Drink water", HabitType.INDIVIDUAL, timeCount = 1))
    }

    @Test fun journal_needsTitleAndTime() {
        assertFalse(habitFormSaveEnabled("Evening reflection", HabitType.JOURNAL, timeCount = 0))
        assertTrue(habitFormSaveEnabled("Evening reflection", HabitType.JOURNAL, timeCount = 1))
    }

    @Test fun batch_needsOnlyTitle() {
        assertTrue(habitFormSaveEnabled("Evening check", HabitType.BATCH, timeCount = 0))
    }

    @Test fun streak_needsOnlyTitle() {
        assertTrue(habitFormSaveEnabled("No smoking", HabitType.STREAK, timeCount = 0))
    }

    @Test fun blankTitle_alwaysFalse() {
        assertFalse(habitFormSaveEnabled("", HabitType.INDIVIDUAL, timeCount = 3))
        assertFalse(habitFormSaveEnabled("   ", HabitType.BATCH, timeCount = 0))
        assertFalse(habitFormSaveEnabled("", HabitType.STREAK, timeCount = 0))
        assertFalse(habitFormSaveEnabled("", HabitType.JOURNAL, timeCount = 3))
    }
}
