package com.daybook.app.ui.journal

import com.daybook.app.data.model.Occurrence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Journal Mode edit-in-place — drives the pure [journalIsEdit]. A journal save updates the loaded
 * occurrence in place (keep responded_at, no duplicate event) exactly when it was already resolved.
 */
class JournalIsEditTest {

    @Test fun nullStatus_backfill_isNotEdit() {
        assertFalse(journalIsEdit(null))
    }

    @Test fun pending_isNotEdit() {
        assertFalse(journalIsEdit(Occurrence.Status.PENDING))
    }

    @Test fun logged_isEdit() {
        assertTrue(journalIsEdit(Occurrence.Status.LOGGED))
    }
}
