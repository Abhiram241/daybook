package com.daybook.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ROUND 0 (Z3) — drives the pure [sheetActionsFor], the Today card overflow sheet's action-list
 * decision extracted out of [ReminderCard]'s `BottomSheetMenu` call. The bug this round fixes was
 * that "Edit" and "Undo" used to be an `else if`: a LOGGED intake row (isLoggedText) got "Edit"
 * and nothing else, so there was no way back to not-logged from the Today card. They are now two
 * independent `if`s and can both appear.
 */
class SheetActionsForTest {

    @Test fun loggedIntake_getsEditAndUndo() {
        assertEquals(
            listOf("Edit", "Undo"),
            sheetActionsFor(
                isLoggedText = true,
                isEditableHabitJournal = false,
                statusLabel = LOGGED_LABEL,
                hasOccurrence = true
            )
        )
    }

    @Test fun loggedHabitJournal_getsEditAndUndo() {
        assertEquals(
            listOf("Edit", "Undo"),
            sheetActionsFor(
                isLoggedText = false,
                isEditableHabitJournal = true,
                statusLabel = LOGGED_LABEL,
                hasOccurrence = true
            )
        )
    }

    @Test fun skippedRow_getsUndoOnly() {
        assertEquals(
            listOf("Undo"),
            sheetActionsFor(
                isLoggedText = false,
                isEditableHabitJournal = false,
                statusLabel = "Skipped",
                hasOccurrence = true
            )
        )
    }

    @Test fun missedRow_getsNoActions() {
        assertEquals(
            emptyList<String>(),
            sheetActionsFor(
                isLoggedText = false,
                isEditableHabitJournal = false,
                statusLabel = MISSED_LABEL,
                hasOccurrence = true
            )
        )
    }

    @Test fun pendingRow_getsSnoozeAndSkip() {
        assertEquals(
            listOf("Snooze", "Skip"),
            sheetActionsFor(
                isLoggedText = false,
                isEditableHabitJournal = false,
                statusLabel = null,
                hasOccurrence = false
            )
        )
    }

    @Test fun resolvedRowWithoutOccurrence_getsNoUndo() {
        // A resolved status label with no local occurrence row (e.g. the row is a synthetic
        // backfill placeholder, not a real Room occurrence) must not offer "Undo" — there is
        // nothing revertFoodMed/revertHabit could act on.
        assertEquals(
            emptyList<String>(),
            sheetActionsFor(
                isLoggedText = false,
                isEditableHabitJournal = false,
                statusLabel = "Done",
                hasOccurrence = false
            )
        )
    }
}
