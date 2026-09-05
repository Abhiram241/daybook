package com.daybook.app.ui.routines

import com.daybook.app.data.model.HabitType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.5 — the `keepStreak` guard in [AddHabitViewModel.saveHabit]. The running day-count
 * (`streak_started_at` / `streak_longest`) survives ONLY a STREAK->STREAK edit; every other
 * (new vs edit) x (old type) x (new type) combination resets it. This is the single most
 * error-prone detail in the round (plan risk HIGH).
 */
class AddHabitViewModelStreakCarryTest {

    // ---- the one true "carry forward" case ----
    @Test fun edit_streakToStreak_carriesForward() {
        assertTrue(keepStreakColumns(isEdit = true, newType = HabitType.STREAK, oldType = HabitType.STREAK))
    }

    // ---- brand-new habit: never carries (there is nothing to carry) ----
    @Test fun new_streak_resets() {
        assertFalse(keepStreakColumns(isEdit = false, newType = HabitType.STREAK, oldType = null))
    }

    @Test fun new_individual_resets() {
        assertFalse(keepStreakColumns(isEdit = false, newType = HabitType.INDIVIDUAL, oldType = null))
    }

    // ---- switching INTO STREAK: reset (previous type had no run) ----
    @Test fun edit_individualToStreak_resets() {
        assertFalse(keepStreakColumns(isEdit = true, newType = HabitType.STREAK, oldType = HabitType.INDIVIDUAL))
    }

    @Test fun edit_batchToStreak_resets() {
        assertFalse(keepStreakColumns(isEdit = true, newType = HabitType.STREAK, oldType = HabitType.BATCH))
    }

    // ---- switching OUT OF STREAK: clear the columns ----
    @Test fun edit_streakToIndividual_clears() {
        assertFalse(keepStreakColumns(isEdit = true, newType = HabitType.INDIVIDUAL, oldType = HabitType.STREAK))
    }

    @Test fun edit_streakToBatch_clears() {
        assertFalse(keepStreakColumns(isEdit = true, newType = HabitType.BATCH, oldType = HabitType.STREAK))
    }

    // ---- non-streak edits are irrelevant but must not trip the guard ----
    @Test fun edit_individualToIndividual_false() {
        assertFalse(keepStreakColumns(isEdit = true, newType = HabitType.INDIVIDUAL, oldType = HabitType.INDIVIDUAL))
    }

    @Test fun edit_streakToStreak_missingOldRow_resets() {
        // getHabitById returned null (row vanished mid-edit) -> cannot prove it was STREAK -> reset.
        assertFalse(keepStreakColumns(isEdit = true, newType = HabitType.STREAK, oldType = null))
    }
}
