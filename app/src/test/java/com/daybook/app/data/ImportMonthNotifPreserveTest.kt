package com.daybook.app.data

import com.daybook.app.data.model.Occurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.3 Phase 1 (S14): a mid-day `importMonth` must reuse a live row's notification_id and must
 * NOT re-insert a still-PENDING row that already has a SHOWN event.
 */
class ImportMonthNotifPreserveTest {

    @Test fun resolveNotifId_reusesExisting() {
        val existing = mapOf("h1:100" to 42)
        var minted = 0
        assertEquals(42, resolveNotifId("h1:100", existing) { minted++; 999 })
        assertEquals(0, minted)
    }

    @Test fun resolveNotifId_mintsForNewId() {
        assertEquals(999, resolveNotifId("h1:200", mapOf("h1:100" to 42)) { 999 })
    }

    @Test fun shouldSkipReinsert_onlyPendingWithShown() {
        assertTrue(shouldSkipReinsert(Occurrence.Status.PENDING, hasShown = true))
        assertFalse(shouldSkipReinsert(Occurrence.Status.PENDING, hasShown = false))
        assertFalse(shouldSkipReinsert(Occurrence.Status.COMPLETED, hasShown = true))
        assertFalse(shouldSkipReinsert(Occurrence.Status.SKIPPED, hasShown = true))
        assertFalse(shouldSkipReinsert(Occurrence.Status.LOGGED, hasShown = true))
    }
}
