package com.daybook.app.data

import com.daybook.app.data.model.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.3 Phase 3 (A3 / A6): guards the retention sweep's two pure helpers. The load-bearing
 * guarantee (audit Q4): only no-content **activity** rows (`SHOWN` / `USER_SNOOZED`) older than
 * `RETENTION_DAYS` are ever prunable — a terminal `REPLIED` / `COMPLETED` / `SKIPPED` row is logged
 * history and is never touched, at any age.
 */
class RetentionSweepTest {

    private val day = 86_400_000L

    @Test fun `SHOWN older than the retention window is prunable`() {
        assertTrue(isPrunableActivity(Event.Action.SHOWN, ageMillis = 91 * day, retentionDays = 90))
    }

    @Test fun `USER_SNOOZED older than the retention window is prunable`() {
        assertTrue(isPrunableActivity(Event.Action.USER_SNOOZED, ageMillis = 91 * day, retentionDays = 90))
    }

    @Test fun `an activity row still inside the window is kept`() {
        assertFalse(isPrunableActivity(Event.Action.SHOWN, ageMillis = 89 * day, retentionDays = 90))
        assertFalse(isPrunableActivity(Event.Action.USER_SNOOZED, ageMillis = 0L, retentionDays = 90))
    }

    @Test fun `exactly at the window is kept (strictly older only)`() {
        assertFalse(isPrunableActivity(Event.Action.SHOWN, ageMillis = 90 * day, retentionDays = 90))
    }

    @Test fun `terminal rows are never prunable, at any age`() {
        val ancient = 3_650L * day // ten years
        for (a in listOf(Event.Action.REPLIED, Event.Action.COMPLETED, Event.Action.SKIPPED)) {
            assertFalse("$a must never be prunable", isPrunableActivity(a, ancient, retentionDays = 90))
            assertFalse("$a must never be prunable", isPrunableActivity(a, ancient, retentionDays = 1))
        }
    }

    @Test fun `retentionCutoffMillis subtracts exactly retentionDays of millis`() {
        val now = 1_800_000_000_000L
        assertEquals(now - 90 * day, retentionCutoffMillis(now, 90))
        assertEquals(now - 30 * day, retentionCutoffMillis(now, 30))
    }

    @Test fun `retentionCutoffMillis defaults to RETENTION_DAYS`() {
        val now = 1_800_000_000_000L
        assertEquals(now - RETENTION_DAYS * day, retentionCutoffMillis(now))
    }
}
