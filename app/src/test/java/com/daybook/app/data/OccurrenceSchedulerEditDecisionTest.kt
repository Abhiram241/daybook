package com.daybook.app.data

import com.daybook.app.data.model.Occurrence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Journal Mode edit-in-place — drives the pure [isFoodMedEdit]. Re-saving a food/med or journal
 * occurrence updates it in place (no new event, keep responded_at, no alarm re-arm) exactly when it
 * is already resolved OR the caller explicitly says so.
 */
class OccurrenceSchedulerEditDecisionTest {

    @Test fun pending_freshCaller_isNotEdit() {
        assertFalse(isFoodMedEdit(Occurrence.Status.PENDING, callerSaysEdit = false))
    }

    @Test fun logged_isEdit_evenIfCallerUnset() {
        assertTrue(isFoodMedEdit(Occurrence.Status.LOGGED, callerSaysEdit = false))
    }

    @Test fun completed_isEdit() {
        assertTrue(isFoodMedEdit(Occurrence.Status.COMPLETED, callerSaysEdit = false))
    }

    @Test fun skipped_isEdit() {
        // Defensive: not reachable via the UI, but a re-save must never re-log a skipped row.
        assertTrue(isFoodMedEdit(Occurrence.Status.SKIPPED, callerSaysEdit = false))
    }

    @Test fun pending_callerSaysEdit_isEdit() {
        assertTrue(isFoodMedEdit(Occurrence.Status.PENDING, callerSaysEdit = true))
    }
}
